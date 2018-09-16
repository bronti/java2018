package ru.au.yaveyn.brontigit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class BrontiGitDir {

    public static Serializer serializer = new Serializer();
    private DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy.MM.dd_HH:mm:ss:SSS");


    private BrontiGitDir() { }

    private Path getCommitFolder(String commitName) {
        return Serializer.COMMITS.resolve(commitName);
    }

    private Path getCommitFile(String commitName) {
        return Serializer.COMMITS.resolve(commitName + ".txt");
    }

    private String getLastCommitName() throws IOException {
        return getAllCommitFiles()
                .filter(Files::isDirectory)
                .map(Path::getFileName)
                .map(Path::toString)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private void checkCommitExistence(String commitName) throws InvalidGitDirException {
        Path commitFile = getCommitFile(commitName);
        Path commitFolder = getCommitFolder(commitName);
        if (!Files.exists(commitFolder) || !Files.exists(commitFile)) {
            throw new InvalidGitDirException("Commit does not exist or git folder is corrupted.");
        }
    }

    private Stream<Path> getAllCommitDataNewerThan(String commitName, boolean includingCurrent) throws IOException {
        return getAllCommitFiles()
                .filter((path) -> {
                    String currentCommitName = path
                            .getFileName()
                            .toString()
                            .replace(".txt", "");
                    int compare = currentCommitName.compareTo(commitName);
                    return (compare > 0) || (includingCurrent && compare == 0);
                });
    }

    private Stream<Path> getAllCommitFiles() throws IOException {
        return Files.list(Serializer.COMMITS);
    }

    private void copyCommitTo(String commitName, Path toFolder, boolean replacing) throws InvalidGitDirException, IOException {
        checkCommitExistence(commitName);
        Path commitFolder = getCommitFolder(commitName);
        for (Path from : Files.walk(commitFolder).collect(Collectors.toList())) {
            if (!from.equals(commitFolder)) {
                Path to = toFolder.resolve(commitFolder.relativize(from));
                if (replacing) {
                    Files.copy(from, to, REPLACE_EXISTING);
                } else {
                    Files.copy(from, to);
                }
            }
        }
    }

    private Stream<Path> dismantle(Path path) {
        List<Path> parents = new ArrayList<>();
        while (path != null) {
            parents.add(path);
            path = path.getParent();
        }
        Collections.reverse(parents);
        return parents.stream();
    }

    public void commit(String msg, List<String> fileNames) throws CommitException, IOException, InvalidGitDirException {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("GMT"));
        String commitName = dtf.format(now);

        String lastCommitName = getLastCommitName();

        Path commitFile = getCommitFile(commitName);
        Path commitFolder = getCommitFolder(commitName);

        if (Files.exists(commitFolder) || Files.exists(commitFile)) {
            throw new CommitException("Commit already exists or git folder is corrupted.");
        }

        try {

            Files.createDirectory(commitFolder);
            Files.createFile(commitFile);

            try (BufferedWriter writer = Files.newBufferedWriter(commitFile, Charset.forName("UTF-8"))) {
                writer.write(commitName);
                writer.write(": ");
                writer.write(msg);
            }

            if (lastCommitName != null) {
                copyCommitTo(lastCommitName, commitFolder, false);
            }

            List<Path> allPaths = fileNames
                    .stream()
                    .map(Paths::get)
                    .map(Path::toAbsolutePath)
                    .map(Serializer.ROOT::relativize)
                    .flatMap(this::dismantle)
                    .distinct()
                    .collect(Collectors.toList());


            for (Path from : allPaths) {
                if (!Files.exists(from)){
                    // todo:check file not from git folder
                    throw new CommitException("File " + from.toString() + " doesn't exist.");
                }
                Path to = commitFolder.resolve(from);
                if (Files.isRegularFile(from)) {
                    Files.copy(from, to, REPLACE_EXISTING);
                } else if (!Files.exists(to)) {
                    Files.copy(from, to);
                }
            }
        } catch (IOException | CommitException | InvalidGitDirException e) {
            Files.delete(commitFile);
            List<Path> commitFiles = Files.walk(commitFolder).collect(Collectors.toList());
            Collections.reverse(commitFiles);
            for (Path path : commitFiles) {
                Files.delete(path);
            }
            throw e;
        }
    }

    public void reset(String commitName) throws InvalidGitDirException, IOException {
        checkCommitExistence(commitName);

        List<Path> laterCommitFiles =
                getAllCommitDataNewerThan(commitName, false).collect(Collectors.toList());

        for (Path path : laterCommitFiles) {
            Files.delete(path);
        }

        // if delete throws IOException git folder can become corrupted ):
    }

    public void checkout(String commitName) throws InvalidGitDirException, IOException {
        checkCommitExistence(commitName);

        copyCommitTo(commitName, Serializer.ROOT,true);
    }

    public String log(String commitName) throws InvalidGitDirException, IOException {
        Stream<Path> relevantCommitsData;
        if (commitName != null) {
            checkCommitExistence(commitName);
            relevantCommitsData = getAllCommitDataNewerThan(commitName, true);
        } else {
            relevantCommitsData = getAllCommitFiles();
        }
        List<Path> commitFiles =
                relevantCommitsData
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList());
        StringBuilder log = new StringBuilder();
        for (Path commitFile: commitFiles) {
            String commit = String.join("\n", Files.readAllLines(commitFile));
            log.append(commit);
            log.append("\n\n");
        }
        return log.toString();
    }

    public static class Serializer {

        private Serializer() {}

        private final static String GIT_DIR_NAME = ".brontiDir";
        private final static Path ROOT = Paths.get(".").toAbsolutePath().normalize();

        final static Path GIT_FOLDER = ROOT.resolve(GIT_DIR_NAME);
        final static Path COMMITS = GIT_FOLDER.resolve("commits");

        private void checkValidGitFolderExists() throws InvalidGitDirException {
            if (!Files.exists(GIT_FOLDER)
                    || !Files.isDirectory(GIT_FOLDER)
                    || !Files.exists(COMMITS)
                    || !Files.isDirectory(COMMITS)) {
                throw new InvalidGitDirException("Git folder is incorrect or nonexistent.");
            }
            // todo: проверить внутрянку
        }

        private void checkGitFolderUninitialized() throws InvalidGitDirException {
            if (Files.exists(GIT_FOLDER)) {
                throw new InvalidGitDirException("Git folder is already initialized.");
            }
            // todo: проверить внутрянку
        }

        private void createGitFolder() throws IOException {
            Files.createDirectory(GIT_FOLDER);
            Files.createDirectory(COMMITS);
        }

        public void init() throws InvalidGitDirException, IOException {
            checkGitFolderUninitialized();
            createGitFolder();
        }

        public BrontiGitDir deserialize() throws InvalidGitDirException {
            checkValidGitFolderExists();
            return new BrontiGitDir();
        }
    }
}
