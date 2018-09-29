package ru.au.yaveyn.brontigit;

import com.google.gson.*;
import ru.au.yaveyn.brontigit.exception.GitException;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class BrontiGitData implements AutoCloseable {
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy.MM.dd_HH:mm:ss:SSS");

    private Generator toCloseWith;

    private Map<String, Commit> commits;
    private Commit pending;
    private String currentCommitName;

    private BrontiGitData(Generator toCloseWith, Map<String, Commit> commits, Commit pending, String currentCommitName) {
        this.toCloseWith = toCloseWith;
        this.commits = commits;
        this.pending = pending;
        this.currentCommitName = currentCommitName;
    }

    private BrontiGitData(Generator toCloseWith) {
        this(toCloseWith, new HashMap<>(), new Commit(), null);
    }

    private boolean isDetached() {
        return pending.getPrevCommit() != null && !pending.getPrevCommit().getName().equals(currentCommitName);
    }

    private void checkCommitExists(String commitName) throws GitException {
        if (!commits.keySet().contains(commitName)) {
            throw new GitException("There is no such commit.");
        }
    }

    private void checkNotDetachedState() throws GitException {
        if (isDetached()) {
            throw new GitException("Your HEAD is detached :( Try resetting to current commit or checkouting last commit.");
        }
    }

    private void checkFileTracked(Path file) throws GitException {
        checkNotDetachedState();
        if (!pending.contains(file)) {
            throw new GitException("File " + file + " is not tracked.");
        }
    }

    private void checkNoPendingChanges() throws GitException {
        if (!isDetached() && !pending.isEmpty()) {
            throw new GitException("There are some uncommitted changes.");
        }
    }

    private void shiftCurrentCommitTo(String commitName) {
        currentCommitName = commitName;
        pending = commits.get(commitName).createNext();
    }

    void add(List<Path> files) throws GitException {
        checkNotDetachedState();
        files.forEach(pending::add);
    }

    List<Path> remove(List<Path> files) throws GitException {
        checkNotDetachedState();
        for (Path file : files) {
            checkFileTracked(file);
        }
        return files.stream().filter(pending::remove).collect(Collectors.toList());
    }

    Commit commit(String msg) throws GitException {
        checkNotDetachedState();
        if (pending.isEmpty()) {
            throw new GitException("Commit is empty.");
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
        String commitName = DTF.format(now);
        commits.put(commitName, pending);
        pending.commit(commitName, msg);

        shiftCurrentCommitTo(pending.getName());

        return commits.get(commitName);
    }

    List<Commit> reset(String commitName) throws GitException {
        checkCommitExists(commitName);
        checkNoPendingChanges();
        List<Commit> commitsToRemove = commits
                .values()
                .stream()
                .filter(entry -> entry.getName().compareTo(commitName) > 0)
                .collect(Collectors.toList());
        commits.entrySet().removeIf(entry -> entry.getKey().compareTo(commitName) > 0);

        shiftCurrentCommitTo(commitName);

        return commitsToRemove;
    }

    Map<Path, Commit> checkoutRevision(String commitName) throws GitException {
        checkCommitExists(commitName);
        checkNoPendingChanges();

        Commit toCheckout = commits.get(commitName);
        Set<Path> affectedFiles = new HashSet<>(pending.getTracked());
        affectedFiles.addAll(toCheckout.getTracked());

        currentCommitName = commitName;

        return affectedFiles.stream().collect(Collectors.toMap(Function.identity(), toCheckout::getLastCommit));
    }

    Map<Path, Commit> checkoutFiles(List<Path> files) throws GitException {
        checkNotDetachedState();
        for (Path file : files) {
            if (pending.isChanged(file)) {
                throw new GitException("There is an uncommitted version of file " + file + ".");
            }
            if (!pending.contains(file)) {
                throw new GitException("There is no previous version of file " + file + ".");
            }
        }
        return files.stream().collect(Collectors.toMap(Function.identity(), pending::getLastCommit));
    }

    public List<Commit> getCommitsFrom(String commitName) throws GitException {
        if (commitName != null) {
            checkCommitExists(commitName);
            if (commitName.compareTo(currentCommitName) > 0) {
                throw new GitException("Commit " + commitName + " happens in the future Oo");
            }
        }
        List<Commit> result = new ArrayList<>();
        Commit current = commits.get(currentCommitName);
        while (current != null && !current.getName().equals(commitName)) {
            result.add(current);
            current = current.getPrevCommit();
        }
        return result;
    }

    public List<Path> getAdded() throws GitException {
        checkNotDetachedState();
        return pending.getAdded();
    }

    public List<Path> getRemoved() throws GitException {
        checkNotDetachedState();
        return pending.getRemoved();
    }

    private static class Serializer implements JsonSerializer<BrontiGitData> {
        @Override
        public JsonElement serialize(BrontiGitData src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            result.addProperty("current", src.currentCommitName);

            JsonArray commits = new JsonArray();
            src.commits
                    .entrySet()
                    .stream()
                    .sorted(Comparator.comparing(Map.Entry::getKey))
                    .forEach(e -> commits.add(context.serialize(e.getValue())));
            result.add("commits", commits);

            result.add("pending", context.serialize(src.pending));
            return result;
        }
    }

    private static class Deserializer implements JsonDeserializer<BrontiGitData> {

        private Commit.Deserializer commitDeserializer = new Commit.Deserializer();
        private Generator toCloseDataWith;

        public Deserializer(Generator toCloseDataWith) {
            this.toCloseDataWith = toCloseDataWith;
        }

        public Commit.Deserializer getCommitDeserializer() {
            return commitDeserializer;
        }

        @Override
        public BrontiGitData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            HashMap<String, Commit> allCommits = new HashMap<>();
            commitDeserializer.renewCommitsMap(allCommits);

            JsonObject src = json.getAsJsonObject();

            String current = src.get("current") == null ? null : src.get("current").getAsString();

            JsonArray commitsSrc = src.get("commits").getAsJsonArray();
            for (JsonElement commitEl : commitsSrc) {
                context.deserialize(commitEl, Commit.class);
            }

            Commit pending = context.deserialize(src.get("pending"), Commit.class);

            return new BrontiGitData(toCloseDataWith, allCommits, pending, current);
        }
    }

    static class Generator {

        private Path root;
        private Gson gson;

        Generator(Path root) {
            this.root = root.toAbsolutePath().normalize();

            BrontiGitData.Deserializer bgdDeser = new BrontiGitData.Deserializer(this);

            gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .registerTypeAdapter(Path.class, new PathTypeAdapter())
                    .registerTypeAdapter(Commit.class, new Commit.Serializer())
                    .registerTypeAdapter(Commit.class, bgdDeser.getCommitDeserializer())
                    .registerTypeAdapter(BrontiGitData.class, new BrontiGitData.Serializer())
                    .registerTypeAdapter(BrontiGitData.class, bgdDeser)
                    .create();
        }

        static final String FILE_NAME = "gitData.json";

        private Path getStorage() {
            return root.resolve(FILE_NAME);
        }

        void serialize(BrontiGitData data) throws IOException {
            try (Writer writer = new FileWriter(getStorage().toString())) {
                gson.toJson(data, writer);
            }
        }

        BrontiGitData init() {
            return new BrontiGitData(this);
        }

        BrontiGitData deserialize() throws IOException {
            try (Reader reader = new FileReader(getStorage().toString())) {
                return gson.fromJson(reader, BrontiGitData.class);
            }
        }
    }

    @Override
    public void close() throws IOException {
        toCloseWith.serialize(this);
    }

    // for testing purposes only

    Commit getPending() {
        return pending;
    }

    Map<String, Commit> getCommits() {
        return commits;
    }
}
