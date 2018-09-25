package ru.au.yaveyn.brontigit;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Commit {
    private String name;
    private final Commit prevCommit;
    private String msg;
    private Map<Path, Commit> files;
    private Set<Path> changedFiles;

    public String getName() {
        return name;
    }

    public String getMsg() {
        return msg;
    }

    public Commit getPrevCommit() {
        return prevCommit;
    }

    private Commit(String name, Commit prevCommit, String msg, Map<Path, Commit> files, Set<Path> changedFiles) {
        this.name = name;
        this.prevCommit = prevCommit;
        this.msg = msg;
        this.files = files;
        this.changedFiles = changedFiles;
    }

    public Commit() {
        this(null, null, null, new HashMap<>(), new HashSet<>());
    }

    public Commit createNext() {
        return new Commit(null, this, null, files, new HashSet<>());
    }

    public void commit(String name, String msg) {
        this.name = name;
        this.msg = msg;
    }

    public boolean contains(Path file) {
        return files.keySet().contains(file);
    }

    public boolean isChanged(Path file) {
        return changedFiles.contains(file);
    }

    public void add(Path file) {
        files.put(file, this);
        changedFiles.add(file);
    }

    public boolean remove(Path file) {
        files.remove(file);
        if (changedFiles.contains(file)) {
            return true;
        } else {
            changedFiles.add(file);
            return false;
        }
    }

    public Commit reset(Path file) {
        files.remove(file);
        if (prevCommit.contains(file)) {
            files.put(file, prevCommit.files.get(file));
        }
        changedFiles.remove(file);
        return files.get(file);
    }

    public boolean isEmpty() {
        return changedFiles.isEmpty();
    }

    static class Serializable {
        private String name;
        private final String prevCommit;
        private String msg;
        private Map<Path, String> files;
        private Set<Path> changedFiles;

        public String getName() {
            return name;
        }

        private Serializable(String name, String prevCommit, String msg, Map<Path, String> files, Set<Path> changedFiles) {
            this.name = name;
            this.prevCommit = prevCommit;
            this.msg = msg;
            this.files = files;
            this.changedFiles = changedFiles;
        }

        public static Serializable fromCommit(Commit commit) {
            Map<Path, String> serializableFiles = commit
                    .files
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                        String name = e.getValue().getName();
                        return name == null ? "" : name;
                    }));

            return new Serializable(
                    commit.getName(),
                    commit.prevCommit == null ? null : commit.prevCommit.getName(),
                    commit.msg,
                    serializableFiles,
                    commit.changedFiles
            );
        }

        public static Commit toCommit(Serializable commit, Map<String, Commit> allCommits) {
            Commit result = new Commit(
                    commit.name,
                    commit.prevCommit == null ? null : allCommits.get(commit.prevCommit),
                    commit.msg,
                    null,
                    commit.changedFiles
            );

            result.files = commit
                    .files
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                        String name = e.getValue();
                        if (name.equals("") || !allCommits.containsKey(name)) {
                            return result;
                        } else {
                            return allCommits.get(name);
                        }
                    }));

            return result;
        }
    }
}
