package ru.au.yaveyn.brontigit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import ru.au.yaveyn.brontigit.exception.GitException;
import ru.au.yaveyn.brontigit.exception.InvalidGitDataException;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class BrontiGitData {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy.MM.dd_HH:mm:ss:SSS");

    private Map<String, Commit> commits;
    private Commit pending;

    private BrontiGitData(Map<String, Commit> commits, Commit pending) {
        this.commits = commits;
        this.pending = pending;
    }

    private BrontiGitData() {
        this(new HashMap<>(), new Commit());
    }

    private void checkCommitExistence(String commitName) throws InvalidGitDataException {
        if (!commits.keySet().contains(commitName)) {
            throw new InvalidGitDataException("There is no such commit.");
        }
    }

    private void checkFileTracked(Path file) throws InvalidGitDataException {
        if (!pending.contains(file)) {
            throw new InvalidGitDataException("File " + file + " is not tracked.");
        }
    }

    private void checkFileChanged(Path file) throws InvalidGitDataException {
        if (!pending.isChanged(file)) {
            throw new InvalidGitDataException("File " + file + " is not changed.");
        }
    }

    private void renewPending() {
        if (pending.getPrevCommit() == null) {
            pending = new Commit();
        } else {
            pending = pending.getPrevCommit().createNext();
        }
    }

    public void add(List<Path> files) {
        files.forEach(pending::add);
    }

    public List<Path> remove(List<Path> files) throws InvalidGitDataException {
        for (Path file : files) {
            checkFileTracked(file);
        }
        return files.stream().filter(pending::remove).collect(Collectors.toList());
    }

    public Commit commit(String msg) throws InvalidGitDataException {
        if (pending.isEmpty()) {
            throw new InvalidGitDataException("Commit is empty.");
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
        String commitName = DTF.format(now);
        commits.put(commitName, pending);
        pending.commit(commitName, msg);
        renewPending();
        return commits.get(commitName);
    }

    public List<Commit> reset(String commitName) throws InvalidGitDataException, GitException {
        checkCommitExistence(commitName);
        if (!pending.isEmpty()) {
            throw new GitException("Cannot reset. There are uncommited files.");
        }
        List<Commit> commitsToRemove = commits
                .values()
                .stream()
                .filter(entry -> entry.getName().compareTo(commitName) > 0)
                .collect(Collectors.toList());
        commits.entrySet().removeIf(entry -> entry.getKey().compareTo(commitName) > 0);
        pending = commits.get(commitName).createNext();
        return commitsToRemove;
    }

    public void checkoutRevision(String commitName) throws InvalidGitDataException {
        checkCommitExistence(commitName);
        // todo: detached head
    }

    public void checkoutFiles(List<Path> files) throws InvalidGitDataException {
        for (Path file : files) {
            checkFileChanged(file);
        }
        files.forEach(pending::reset);
    }

    public static class Serializer {

        private Path root;

        public Serializer(Path root) {
            this.root = root.toAbsolutePath().normalize();
        }

        private static final String PENDING_FILE_NAME = "pending.json";
        private static final String COMMITS_FILE_NAME = "commits.json";

        private Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Path.class, new PathTypeAdapter())
                .create();

        private Path getPendingFile() {
            return root.resolve(PENDING_FILE_NAME);
        }

        private Path getCommitsFile() {
            return root.resolve(COMMITS_FILE_NAME);
        }

        public void serialize(BrontiGitData data) throws IOException {
            try (Writer pendingWriter = new FileWriter(getPendingFile().toString())) {
                gson.toJson(Commit.Serializable.fromCommit(data.pending), pendingWriter);
            }
            try (Writer commitsWriter = new FileWriter(getCommitsFile().toString())) {
                gson.toJson(
                        data.commits.values().stream().map(Commit.Serializable::fromCommit).collect(Collectors.toList()),
                        commitsWriter
                );
            }
        }

        public BrontiGitData init() {
            return new BrontiGitData();
        }

        public BrontiGitData deserialize() throws IOException {
            try (
                    Reader pendingReader = new FileReader(getPendingFile().toString());
                    Reader commitsReader = new FileReader(getCommitsFile().toString())
            ) {
                Type commitsType = new TypeToken<List<Commit.Serializable>>(){}.getType();
                List<Commit.Serializable> serializables = gson.fromJson(commitsReader, commitsType);
                Map<String, Commit> commits = new HashMap<>();
                serializables.forEach(c -> commits.put(c.getName(), Commit.Serializable.toCommit(c, commits)));
                Commit.Serializable serializablePending = gson.fromJson(pendingReader, Commit.Serializable.class);
                Commit pending = Commit.Serializable.toCommit(serializablePending, commits);
                return new BrontiGitData(commits, pending);
            }
        }
    }

    // for testing purposes only

    Commit getPending() {
        return pending;
    }

    Map<String, Commit> getCommits() {
        return commits;
    }
}
