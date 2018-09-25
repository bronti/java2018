package ru.au.yaveyn.brontigit;


import ru.au.yaveyn.brontigit.exception.GitException;
import ru.au.yaveyn.brontigit.exception.InvalidGitDataException;
import ru.au.yaveyn.brontigit.exception.InvalidGitDirException;
import ru.au.yaveyn.brontigit.exception.InvalidUsageException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BrontiCLI {

    private static final Path ROOT = Paths.get(".").toAbsolutePath().normalize();

    private static void checkAtLeastXArgs(String[] args, int x) throws InvalidUsageException {
        if (args.length < x) {
            throw new InvalidUsageException();
        }
    }

    private static void checkXArgsSharp(String[] args, int x) throws InvalidUsageException {
        if (args.length != x) {
            throw new InvalidUsageException();
        }
    }

    private static void checkNoMoreThanXArgs(String[] args, int x) throws InvalidUsageException {
        if (args.length > x) {
            throw new InvalidUsageException();
        }
    }

    private static List<Path> fileNamesToPaths(List<String> fileNames) {
        return fileNames
                .stream()
                .map(Paths::get)
                .map(Path::normalize)
                .map(ROOT::resolve)
                .map(ROOT::relativize)
                .map(Path::normalize)
                .collect(Collectors.toList());
    }

    public static void run(String[] args) {

        BrontiGit git = new BrontiGit(ROOT);

        try {
            if (args[0].equals("init")) {
                checkXArgsSharp(args, 1);
                git.init();
                return;
            }

            switch (args[0]) {
                case "add" : {
                    checkAtLeastXArgs(args, 2);
                    List<String> fileNames = Arrays.asList(args).subList(1, args.length);
                    git.add(fileNamesToPaths(fileNames));
                    break;
                }
                case "rm" : {
                    checkAtLeastXArgs(args, 2);
                    List<String> fileNames = Arrays.asList(args).subList(1, args.length);
                    git.remove(fileNamesToPaths(fileNames));
                    break;
                }
                case "status" : {
                    checkXArgsSharp(args, 1);
                    // todo:
                    break;
                }
                case "commit" : {
                    checkXArgsSharp(args, 2);
                    String commitMsg = args[1];
                    git.commit(commitMsg);
                    break;
                }
                case "reset" : {
                    checkXArgsSharp(args, 2);
                    String commitName = args[1];
                    git.reset(commitName);
                    break;
                }
                case "log" : {
                    checkAtLeastXArgs(args, 1);
                    checkNoMoreThanXArgs(args, 2);
                    String commitName = null;
                    if (args.length == 2) {
                        commitName = args[1];
                    }
                    // todo:
                    break;
                }
                case "checkout" : {
                    checkAtLeastXArgs(args, 2);
                    if (args[1].equals("--")) {
                        checkAtLeastXArgs(args, 3);
                        List<String> fileNames = Arrays.asList(args).subList(2, args.length);
//                        data.checkoutFiles(fileNamesToPaths(fileNames));
                    } else {
                        checkXArgsSharp(args, 2);
                        String commitName = args[1];
//                        data.checkoutRevision(commitName);
                    }
                    break;
                }
                default: throw new InvalidUsageException();
            }
        } catch (IOException e) {
            System.out.println("IOException occured, bronti git folder may be corrupted.");
            e.printStackTrace();
        } catch (InvalidGitDataException | InvalidGitDirException | GitException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        } catch (InvalidUsageException e) {
            System.out.println("Invalid command.");
            e.printStackTrace();
        }
    }
}
