package ru.au.yaveyn.brontigit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import ru.au.yaveyn.brontigit.exception.GitException;
import ru.au.yaveyn.brontigit.exception.InvalidGitDirException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class GitTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path root;
    private Path gitFolder;
    private Path commitsFolder;
    private Path headFolder;
    private Path dataFile;


    @Before
    public void before() throws IOException {
        root = Paths.get(tmp.newFolder("root").getAbsolutePath());
        gitFolder = root.resolve(".brontiGit");
        commitsFolder = gitFolder.resolve("commits");
        headFolder = commitsFolder.resolve("head");
        dataFile = gitFolder.resolve(BrontiGitData.Generator.FILE_NAME);
    }

    static private void assertFolder(Path folder) {
        assertTrue(Files.exists(folder) && Files.isDirectory(folder));
    }

    static private void assertFile(Path file) {
        assertTrue(Files.exists(file) && Files.isRegularFile(file));
    }

    private void assertCorrectGitFolder() {
        assertFolder(gitFolder);
        assertFolder(commitsFolder);
        assertFolder(headFolder);
        assertFile(dataFile);
    }

    private BrontiGitData getData() throws IOException {
        return new BrontiGitData.Generator(gitFolder).deserialize();
    }

    @Test
    public void testInit() throws IOException, GitException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();

        BrontiGitData data = getData();
        assertTrue(data.getCommits().isEmpty());
        assertTrue(data.getPending().isEmpty());
    }

    @Test
    public void testAddFile() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd = Files.createFile(root.resolve("toAdd.txt"));
        git.add(Collections.singletonList(toAdd));

        assertFile(headFolder.resolve("toAdd.txt"));

        BrontiGitData data = getData();
        assertTrue(data.getCommits().isEmpty());
        assertFalse(data.getPending().isEmpty());
        assertTrue(data.getPending().contains(toAdd));
        assertTrue(data.getPending().isChanged(toAdd));
    }

    @Test
    public void testAddFiles() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd1 = Files.createFile(root.resolve("toAdd1.txt"));
        Path toAdd2 = Files.createFile(root.resolve("toAdd2.txt"));
        git.add(Collections.singletonList(toAdd1));
        git.add(Collections.singletonList(toAdd2));

        assertFile(headFolder.resolve("toAdd1.txt"));
        assertFile(headFolder.resolve("toAdd2.txt"));

        BrontiGitData data = getData();
        assertTrue(data.getCommits().isEmpty());
        assertFalse(data.getPending().isEmpty());
        assertTrue(data.getPending().contains(toAdd1));
        assertTrue(data.getPending().contains(toAdd2));
        assertTrue(data.getPending().isChanged(toAdd1));
        assertTrue(data.getPending().isChanged(toAdd2));
    }

    @Test
    public void testAddFileInFolder() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path dir = Files.createDirectory(root.resolve("dir"));
        Path toAdd = Files.createFile(dir.resolve("toAdd.txt"));
        git.add(Collections.singletonList(toAdd));

        assertFile(headFolder.resolve("dir").resolve("toAdd.txt"));

        BrontiGitData data = getData();
        assertTrue(data.getCommits().isEmpty());
        assertFalse(data.getPending().isEmpty());
        assertTrue(data.getPending().contains(toAdd));
        assertTrue(data.getPending().isChanged(toAdd));
    }

    @Test
    public void testCommit() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd = Files.createFile(root.resolve("toAdd.txt"));
        git.add(Collections.singletonList(toAdd));
        git.commit("msg");

        BrontiGitData data = getData();
        assertTrue(data.getPending().isEmpty());
        Assert.assertEquals(1, data.getCommits().size());

        Commit commit = data.getCommits().entrySet().iterator().next().getValue();
        Path currCommitFolder = commitsFolder.resolve(commit.getName());

        assertFolder(currCommitFolder);
        assertFile(currCommitFolder.resolve("toAdd.txt"));
    }

    @Test
    public void testCommits() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd1 = Files.createFile(root.resolve("toAdd1"));
        Path toAdd2 = Files.createFile(root.resolve("toAdd2"));
        Path toAdd3 = Files.createFile(root.resolve("toAdd3"));
        git.add(Collections.singletonList(toAdd1));
        git.commit("1");
        git.add(Collections.singletonList(toAdd2));
        git.commit("2");
        git.add(Collections.singletonList(toAdd3));
        git.commit("3");

        BrontiGitData data = getData();
        assertTrue(data.getPending().isEmpty());
        Assert.assertEquals(3, data.getCommits().size());

        for (Commit commit : data.getCommits().values()) {
            Path commitFolder = commitsFolder.resolve(commit.getName());
            assertFolder(commitFolder);
            assertFile(commitFolder.resolve("toAdd" + commit.getMsg()));
        }
    }

    @Test
    public void testRemoveAlreadyCommitted() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd = Files.createFile(root.resolve("toAdd"));
        git.add(Collections.singletonList(toAdd));
        git.commit("0");

        try (Writer writer = new FileWriter(toAdd.toString())) {
            writer.write("change");
        }

        git.add(Collections.singletonList(toAdd));
        git.remove(Collections.singletonList(toAdd));

        assertFalse(Files.exists(headFolder.resolve("toAdd")));
        assertFile(toAdd);

        BrontiGitData data = getData();
        assertFalse(data.getPending().isEmpty());
    }

    @Test
    public void testRemoveJustAdded() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd = Files.createFile(root.resolve("toAdd.txt"));
        git.add(Collections.singletonList(toAdd));
        git.remove(Collections.singletonList(toAdd));

        assertFalse(Files.exists(headFolder.resolve("toAdd.txt")));
        assertFile(toAdd);

        BrontiGitData data = getData();
        assertTrue(data.getPending().isEmpty());
    }

    @Test
    public void testReset() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd1 = Files.createFile(root.resolve("toAdd1"));
        Path toAdd2 = Files.createFile(root.resolve("toAdd2"));
        Path toAdd3 = Files.createFile(root.resolve("toAdd3"));

        git.add(Collections.singletonList(toAdd1));
        Commit toReset = git.commit("1");
        git.add(Collections.singletonList(toAdd2));
        git.commit("2");
        git.add(Collections.singletonList(toAdd3));
        git.commit("3");

        git.reset(toReset.getName());

        BrontiGitData data = getData();
        assertTrue(data.getPending().isEmpty());
        Assert.assertEquals(1, data.getCommits().size());

        for (Commit commit : data.getCommits().values()) {
            Path commitFolder = commitsFolder.resolve(commit.getName());
            Path commitFile = commitFolder.resolve("toAdd" + commit.getMsg());
            if (commit == toReset) {
                assertFalse(Files.exists(commitFolder));
            } else {
                assertFolder(commitFolder);
                assertFile(commitFile);
            }
        }
    }

    @Test
    public void testCheckoutFiles() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd = Files.createFile(root.resolve("toAdd"));

        git.add(Collections.singletonList(toAdd));
        git.commit("1");

        try (Writer writer = new FileWriter(toAdd.toString())) {
            writer.write("change\n");
        }

        git.add(Collections.singletonList(toAdd));
        git.commit("2");

        try (Writer writer = new FileWriter(toAdd.toString())) {
            writer.write("ololo\nsecond change\n");
        }

        git.checkoutFiles(Collections.singletonList(toAdd));

        assertEquals(1, Files.readAllLines(toAdd).size());
    }

    @Test
    public void testCheckoutRevision() throws IOException, GitException, InvalidGitDirException {
        BrontiGit git = new BrontiGit(root);
        git.init();
        assertCorrectGitFolder();
        Path toAdd = Files.createFile(root.resolve("toAdd"));
        Path toAdd2 = Files.createFile(root.resolve("toAdd2"));


        try (Writer writer = new FileWriter(toAdd.toString())) {
            writer.write("change\n");
        }

        git.add(Collections.singletonList(toAdd));
        git.commit("0");

        git.add(Collections.singletonList(toAdd2));
        Commit toCheckout = git.commit("1");

        try (Writer writer = new FileWriter(toAdd.toString())) {
            writer.write("ololo\nsecond change\n");
        }

        git.add(Collections.singletonList(toAdd));
        git.commit("2");

        git.checkoutRevision(toCheckout.getName());

        assertEquals(1, Files.readAllLines(toAdd).size());
    }
}
