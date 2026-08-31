package com.codebasecartographer.api.service;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.dto.ASTChunk;

import lombok.extern.slf4j.Slf4j;

/**
 * Pure Java text splitter that replaces the native Tree-Sitter JNI implementation.
 * 
 * WHY WE REPLACED TREE-SITTER:
 * Tree-Sitter relies on a native C library (libjava-tree-sitter.so) that must be compiled
 * for the exact OS and architecture of the host machine. This caused a fatal
 * UnsatisfiedLinkError (libc.musl-x86_64.so.1 not found) on non-glibc systems, 
 * silently killing our background worker threads and causing the SSE stream to hang forever.
 * Because UnsatisfiedLinkError is a java.lang.Error (not Exception), our standard 
 * try-catch blocks couldn't even catch it.
 * 
 * HOW THIS WORKS:
 * This class uses a "Recursive Character Text Splitter" strategy, inspired by LangChain.
 * It splits source code into overlapping chunks by trying progressively smaller separators:
 *   1. First tries double newlines (\n\n) — splits at function/class boundaries
 *   2. Then single newlines (\n) — splits at line boundaries
 *   3. Then spaces — splits at word boundaries
 *   4. Finally, hard character split — brute-force for very long lines
 * 
 * The overlap ensures that context is not lost at chunk boundaries, which is critical
 * for the embedding model to understand code that spans multiple chunks.
 */
@Slf4j
@Service
public class RecursiveTextSplitter {

    // Target size for each chunk in characters (~1000 chars ≈ ~250 tokens)
    private static final int CHUNK_SIZE = 1000;

    // Overlap between consecutive chunks to preserve context at boundaries
    private static final int CHUNK_OVERLAP = 200;

    // Separators ordered from most desirable (preserves structure) to least
    private static final String[] SEPARATORS = {"\n\n", "\n", " ", ""};

    /**
     * Splits raw source code into overlapping chunks, returning ASTChunk DTOs
     * that are compatible with the existing CodeChunk entity mapping in IndexingService.
     * 
     * @param code The raw file content as a string
     * @return A list of ASTChunk objects, each representing one chunk of the file
     */
    public List<ASTChunk> chunkCode(String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }

        // Delegate to the recursive splitting algorithm starting with the broadest separator
        List<String> rawChunks = splitText(code, 0);
        
        // Apply overlap once globally on the contiguous chunks
        List<String> textChunks = applyOverlap(rawChunks);

        // Convert raw text chunks into ASTChunk DTOs with line number metadata
        List<ASTChunk> result = new ArrayList<>();
        int charOffset = 0;

        for (int i = 0; i < rawChunks.size(); i++) {
            String rawChunk = rawChunks.get(i);
            String chunk = textChunks.get(i);
            
            // Calculate approximate line numbers based on exact character offset
            // The chunk actually starts at charOffset - OVERLAP (if i > 0)
            int actualStartOffset = (i == 0) ? 0 : Math.max(0, charOffset - CHUNK_OVERLAP);
            int startLine = countLines(code, 0, actualStartOffset) + 1;
            int endLine = startLine + countNewlines(chunk);

            result.add(ASTChunk.builder()
                    .content(chunk)
                    .startLine(startLine)
                    .endLine(endLine)
                    .scopeChain(null)
                    .entityName(null)
                    .chunkType("MODULE")  // Pure text splitting can't determine function/class types
                    .build());

            // Advance offset by the exact length of the non-overlapping segment
            charOffset += rawChunk.length();
        }

        log.debug("Split code into {} chunks (avg {} chars each)", result.size(),
                result.isEmpty() ? 0 : code.length() / result.size());
        return result;
    }

    /**
     * Core recursive algorithm. Tries to split text using the current separator.
     * If any resulting piece is still too large, recurse with a finer separator.
     */
    private List<String> splitText(String text, int separatorIndex) {
        if (text.length() <= CHUNK_SIZE) {
            // Base case: text fits in one chunk, no need to split
            return List.of(text);
        }

        if (separatorIndex >= SEPARATORS.length) {
            // Fallback: no more separators left, hard-split by character count
            return hardSplit(text);
        }

        String separator = SEPARATORS[separatorIndex];
        String[] splits;

        if (separator.isEmpty()) {
            // Empty separator = split every character (last resort)
            return hardSplit(text);
        } else {
            splits = text.split(Pattern.quote(separator), -1);
        }

        // Merge small splits back together until they approach CHUNK_SIZE
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String split : splits) {
            String candidate = currentChunk.isEmpty()
                    ? split
                    : currentChunk + separator + split;

            if (candidate.length() <= CHUNK_SIZE) {
                // Still fits, keep accumulating
                currentChunk = new StringBuilder(candidate);
            } else {
                // Adding this split would exceed the limit
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                }

                // If this individual split is too large, recurse with a finer separator
                if (split.length() > CHUNK_SIZE) {
                    chunks.addAll(splitText(split, separatorIndex + 1));
                    currentChunk = new StringBuilder();
                } else {
                    currentChunk = new StringBuilder(split);
                }
            }
        }

        // Don't forget the last accumulated chunk
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }

        // Return contiguous, non-overlapping chunks. Overlap is applied once at the top level.
        return chunks;
    }

    /**
     * Brute-force split for text with no usable separators (e.g., a minified JS file).
     * Splits at exact character boundaries.
     */
    private List<String> hardSplit(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start += CHUNK_SIZE; // Do not apply overlap here, done at top level
        }
        return chunks;
    }

    /**
     * Adds character overlap between consecutive chunks.
     * Each chunk's tail overlaps with the next chunk's head by CHUNK_OVERLAP characters.
     * This ensures that embedding models don't lose context at chunk boundaries.
     */
    private List<String> applyOverlap(List<String> chunks) {
        if (chunks.size() <= 1 || CHUNK_OVERLAP <= 0) {
            return chunks;
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (i == 0) {
                result.add(chunks.get(i));
            } else {
                // Prepend the tail of the previous chunk as overlap context
                String prev = chunks.get(i - 1);
                int overlapStart = Math.max(0, prev.length() - CHUNK_OVERLAP);
                String overlap = prev.substring(overlapStart);
                result.add(overlap + chunks.get(i));
            }
        }
        return result;
    }

    /** Count total lines from start to endOffset in the given text */
    private int countLines(String text, int start, int endOffset) {
        int count = 0;
        int limit = Math.min(endOffset, text.length());
        for (int i = start; i < limit; i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    /** Count newline characters in a string */
    private int countNewlines(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }
}
