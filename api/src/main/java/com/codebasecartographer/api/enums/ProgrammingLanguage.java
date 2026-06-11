package com.codebasecartographer.api.enums;

import java.util.List;

public enum ProgrammingLanguage {
    JAVA("java", ".java"),
    TYPESCRIPT("typescript", ".ts", ".tsx"),
    JAVASCRIPT("javascript", ".js", ".jsx"),
    PYTHON("python", ".py"),
    GO("go", ".go"),
    RUST("rust", ".rs"),
    CPP("cpp", ".cpp", ".c", ".h", ".hpp");

    private final String treeSitterName;
    private final List<String> extensions;

    ProgrammingLanguage(String treeSitterName, String... extensions) {
        this.treeSitterName = treeSitterName;
        this.extensions = List.of(extensions);
    }

    public String getTreeSitterName() { return treeSitterName; }
    public List<String> getExtensions() { return extensions; }

    public static ProgrammingLanguage fromExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) return null;
        int dot = filePath.lastIndexOf('.');
        if (dot < 0) return null;
        String ext = filePath.substring(dot);
        for (ProgrammingLanguage lang : values()) {
            if (lang.extensions.contains(ext)) {
                return lang;
            }
        }
        return null;
    }
}
