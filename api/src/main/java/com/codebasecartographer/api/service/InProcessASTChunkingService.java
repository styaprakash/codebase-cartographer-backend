package com.codebasecartographer.api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.codebasecartographer.api.dto.ASTChunk;
import com.codebasecartographer.api.enums.ProgrammingLanguage;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Tree;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InProcessASTChunkingService {

    private static final int FALLBACK_LINE_LIMIT = 500;

    private static final Set<String> FUNCTION_TYPES = Set.of(
        "function_definition",
        "method_definition",
        "method_declaration",
        "function_declaration",
        "arrow_function",
        "generator_function",
        "function"
    );

    private static final Set<String> CLASS_TYPES = Set.of(
        "class_declaration",
        "class_definition"
    );

    private static final Map<ProgrammingLanguage, Language> LANGUAGE_MAP = Map.of(
        ProgrammingLanguage.JAVA, Language.JAVA,
        ProgrammingLanguage.PYTHON, Language.PYTHON,
        ProgrammingLanguage.TYPESCRIPT, Language.TYPESCRIPT,
        ProgrammingLanguage.JAVASCRIPT, Language.TYPESCRIPT,
        ProgrammingLanguage.GO, Language.GO,
        ProgrammingLanguage.RUST, Language.RUST,
        ProgrammingLanguage.CPP, Language.CPP
    );

    public List<ASTChunk> chunkCode(String code, ProgrammingLanguage language) {
        if (code == null || code.isBlank()) {
            return List.of();
        }

        Language tsLanguage = language != null ? LANGUAGE_MAP.get(language) : null;
        if (tsLanguage == null) {
            log.warn("Unsupported or null language {}, falling back to line-based chunking", language);
            return fallbackChunkByLines(code);
        }

        try (Parser parser = Parser.getFor(tsLanguage);
            Tree tree = parser.parse(code)) {

            Node root = tree.getRootNode();
            List<ASTChunk> chunks = new ArrayList<>();
            for (int i = 0; i < root.getChildCount(); i++) {
                walkNode(root.getChild(i), code, null, chunks);
            }

            if (chunks.isEmpty()) {
                log.debug("AST parsing produced no chunks for {}, falling back to line-based", tsLanguage);
                return fallbackChunkByLines(code);
            }

            return chunks;

        } catch (Exception e) {
            log.warn("AST parsing failed for language {}: {}", language, e.getMessage());
            return fallbackChunkByLines(code);
        }
    }

    private void walkNode(Node node, String code, String parentScope, List<ASTChunk> chunks) {
        if (node.isError() || node.isNull()) return;

        String type = node.getType();
        String chunkType = resolveChunkType(type);
        if (chunkType == null) {
            for (int i = 0; i < node.getChildCount(); i++) {
                walkNode(node.getChild(i), code, parentScope, chunks);
            }
            return;
        }

        String entityName = extractName(node);
        String scopeChain = buildScopeChain(parentScope, entityName, type);

        int startLine = node.getStartPoint().getRow() + 1;
        int endLine = node.getEndPoint().getRow() + 1;
        int startByte = node.getStartByte();
        int endByte = node.getEndByte();

        String content = (startByte >= 0 && endByte <= code.length())
            ? code.substring(startByte, endByte)
            : "";

        chunks.add(ASTChunk.builder()
                .content(content)
                .startLine(startLine)
                .endLine(endLine)
                .entityName(entityName)
                .scopeChain(scopeChain)
                .chunkType(chunkType)
                .build());

        String newScope = entityName != null ? scopeChain : parentScope;
        for (int i = 0; i < node.getChildCount(); i++) {
            walkNode(node.getChild(i), code, newScope, chunks);
        }
    }

    private static String resolveChunkType(String nodeType) {
        if (FUNCTION_TYPES.contains(nodeType)) return "FUNCTION";
        if (CLASS_TYPES.contains(nodeType)) return "CLASS";
        return null;
    }

    private static String extractName(Node node) {
        Node nameNode = node.getChildByFieldName("name");
        if (nameNode != null && !nameNode.isNull()) {
            String name = nameNode.getContent();
            if (name != null && !name.isBlank()) return name;
        }
        return null;
    }

    private static String buildScopeChain(String parentScope, String entityName, String nodeType) {
        String name = entityName;
        if (name == null) {
            if (CLASS_TYPES.contains(nodeType)) name = "<anonymous_class>";
            else name = "<anonymous_function>";
        }
        return parentScope != null ? parentScope + " > " + name : name;
    }

    private List<ASTChunk> fallbackChunkByLines(String code) {
        List<ASTChunk> chunks = new ArrayList<>();
        if (code == null || code.isBlank()) return chunks;

        String[] lines = code.split("\n", -1);
        int totalLines = lines.length;
        int start = 1;

        while (start <= totalLines) {
            int end = Math.min(start + FALLBACK_LINE_LIMIT - 1, totalLines);
            StringBuilder content = new StringBuilder();
            for (int i = start - 1; i < end; i++) {
                content.append(lines[i]).append("\n");
            }

            chunks.add(ASTChunk.builder()
                    .content(content.toString().stripTrailing())
                    .startLine(start)
                    .endLine(end)
                    .scopeChain(null)
                    .entityName(null)
                    .chunkType("MODULE")
                    .build());

            start = end + 1;
        }

        return chunks;
    }
}
