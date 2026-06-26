package com.codebasecartographer.api.service;

// ============================================================================
// DISABLED — This class has been replaced by RecursiveTextSplitter.java
// 
// The native Tree-Sitter JNI library (libjava-tree-sitter.so) was causing a 
// fatal UnsatisfiedLinkError on non-glibc systems, silently killing worker
// threads. Since UnsatisfiedLinkError is a java.lang.Error (not Exception),
// our standard try-catch blocks couldn't even catch it, causing the SSE 
// stream to hang indefinitely.
//
// The tree-sitter Maven dependency has also been removed from pom.xml.
// If you ever need to restore AST-level chunking, you would need to:
//   1. Re-add the ch.usi.si.seart:java-tree-sitter dependency to pom.xml
//   2. Ensure the native .so is compiled for your exact OS/arch
//   3. Restore the original class body from version control
// ============================================================================
