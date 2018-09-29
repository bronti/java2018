package ru.au.yaveyn.brontigit;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public Set<Path> getTracked() {
        return files.keySet();
    }

    Commit getLastCommit(Path file) {
        return files.get(file);
    }

    public boolean isEmpty() {
        return changedFiles.isEmpty();
    }

    private Commit(String name, Commit prevCommit, String msg, Map<Path, Commit> files, Set<Path> changedFiles) {
        this.name = name;
        this.prevCommit = prevCommit;
        this.msg = msg;
        this.files = files;
        this.changedFiles = changedFiles;
    }

    Commit() {
        this(null, null, null, new HashMap<>(), new HashSet<>());
    }

    Commit createNext() {
        return new Commit(null, this, null, files, new HashSet<>());
    }

    void commit(String name, String msg) {
        this.name = name;
        this.msg = msg;
    }

    public boolean contains(Path file) {
        return files.keySet().contains(file);
    }

    public boolean isChanged(Path file) {
        return changedFiles.contains(file);
    }

    public List<Path> getAdded() {
        return changedFiles.stream().filter(this::contains).collect(Collectors.toList());
    }

    public List<Path> getRemoved() {
        return changedFiles.stream().filter(f -> !this.contains(f)).collect(Collectors.toList());
    }

    void add(Path file) {
        files.put(file, this);
        changedFiles.add(file);
    }

    boolean remove(Path file) {
        assert this.contains(file);
        files.remove(file);
        if (!changedFiles.contains(file)) {
            changedFiles.add(file);
            return false;
        }
        if (prevCommit == null || !prevCommit.contains(file)) {
            changedFiles.remove(file);
            // todo: fix this, so prevCommit doesnt have to be stored
        }
        return true;
    }

    public static class Serializer implements JsonSerializer<Commit> {

        public JsonElement serialize(Commit src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();
            result.addProperty("name", src.name);
            result.addProperty("prev", src.prevCommit == null ? null : src.prevCommit.getName());
            result.addProperty("msg", src.msg);

            JsonArray changed = new JsonArray();
            src.changedFiles.forEach(f -> changed.add(context.serialize(f, Path.class)));
            result.add("changed", changed);

            JsonArray allFiles = new JsonArray();
            src.files.forEach((k, v) -> {
                        JsonArray entry = new JsonArray();
                        entry.add(context.serialize(k, Path.class));
                        entry.add((v == src ? "" : v.getName()));
                        allFiles.add(entry);
                    });

            result.add("files", allFiles);
            return result;
        }
    }

    public static class Deserializer implements JsonDeserializer<Commit> {
        Map<String, Commit> allCommits;

        // get this class instance only through BrontiGitData.Deserializer
        Deserializer() {
        }

        void renewCommitsMap(Map<String, Commit> allCommits) {
            this.allCommits = allCommits;
        }

        @Override
        public Commit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject src = json.getAsJsonObject();

            String name = src.get("name") == null ? null : src.get("name").getAsString();

            JsonElement prevSrc = src.get("prev");
            Commit prevCommit = prevSrc == null ? null : allCommits.get(prevSrc.getAsString());

            String msg = src.get("msg") == null ? null : src.get("msg").getAsString();

            Commit result = new Commit(name, prevCommit, msg, null, null);
            if (name != null) {
                allCommits.put(name, result);
            }

            Map<Path, Commit> files = new HashMap<>();
            JsonArray filesSrc = src.get("files").getAsJsonArray();
            for (JsonElement fileEl : filesSrc) {
                JsonArray fileSrc = fileEl.getAsJsonArray();
                Path file = context.deserialize(fileSrc.get(0), Path.class);
                String commitName = fileSrc.get(1).getAsString();
                Commit commit = commitName.equals("") ? result : allCommits.get(commitName);
                files.put(file, commit);
            }
            result.files = files;

            Set<Path> changedFiles = new HashSet<>();
            JsonArray changedSrc = src.get("changed").getAsJsonArray();
            for (JsonElement changedEl : changedSrc) {
                changedFiles.add(context.deserialize(changedEl, Path.class));
            }
            result.changedFiles = changedFiles;
            result.files = files;

            return result;
        }
    }
}
