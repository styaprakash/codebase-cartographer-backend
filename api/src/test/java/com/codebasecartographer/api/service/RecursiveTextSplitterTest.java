package com.codebasecartographer.api.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.codebasecartographer.api.dto.ASTChunk;
import java.util.List;

class RecursiveTextSplitterTest {

    private final RecursiveTextSplitter splitter = new RecursiveTextSplitter();

    @Test
    void testOverlapBugWithMultiLevelRecursion() {
        // Create a 1500-character string with NO separators (\n or space)
        // This will force the splitter to recurse all the way down to hardSplit
        // Without the fix, the overlap would be applied multiple times,
        // resulting in duplicated text and massive length bloat.
        StringBuilder sb = new StringBuilder(1500);
        for (int i = 0; i < 150; i++) {
            sb.append("0123456789"); // 10 chars
        }
        String text = sb.toString();
        assertEquals(1500, text.length());

        List<ASTChunk> chunks = splitter.chunkCode(text);

        // Expected chunks:
        // Chunk 1: chars 0 to 1000. Length = 1000.
        // Chunk 2: chars 800 to 1500 (200 overlap + 500 new chars). Length = 700.
        assertEquals(2, chunks.size(), "Should split into exactly 2 chunks");

        ASTChunk chunk1 = chunks.get(0);
        assertEquals(1000, chunk1.getContent().length(), "Chunk 1 should be exactly 1000 characters");
        assertEquals(text.substring(0, 1000), chunk1.getContent());

        ASTChunk chunk2 = chunks.get(1);
        assertEquals(700, chunk2.getContent().length(), "Chunk 2 should be exactly 700 characters (200 overlap + 500 new)");
        assertEquals(text.substring(800, 1500), chunk2.getContent());
        
        // Check start and end lines (since there are no newlines, everything is on line 1)
        assertEquals(1, chunk1.getStartLine());
        assertEquals(1, chunk1.getEndLine());
        assertEquals(1, chunk2.getStartLine());
        assertEquals(1, chunk2.getEndLine());
    }
}
