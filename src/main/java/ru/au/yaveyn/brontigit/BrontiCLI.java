package ru.au.yaveyn.brontigit;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class BrontiCLI {

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

    public static void run(String[] args) {

        BrontiGitDir.Serializer gitDirSerializer = BrontiGitDir.serializer;

        try {
            checkAtLeastXArgs(args, 1);
            if (args[0].equals("init")) {
                checkXArgsSharp(args, 1);
                gitDirSerializer.init();
                return;
            }

            BrontiGitDir gitDir = gitDirSerializer.deserialize();

            switch (args[0]) {
                case "commit" : {
                    checkAtLeastXArgs(args, 2);
                    String commitMsg = args[1];
                    List<String> fileNames = Arrays.asList(args).subList(2, args.length);
                    gitDir.commit(commitMsg, fileNames);
                    break;
                }
                case "reset" : {
                    checkXArgsSharp(args, 2);
                    String commitName = args[1];
                    gitDir.reset(commitName);
                    break;
                }
                case "checkout" : {
                    checkXArgsSharp(args, 2);
                    String commitName = args[1];
                    gitDir.checkout(commitName);
                    break;
                }
                case "log" : {
                    checkNoMoreThanXArgs(args, 2);
                    String commitName = null;
                    if (args.length == 2) {
                        commitName = args[1];
                    }
                    System.out.println(gitDir.log(commitName));
                    break;
                }
                default: throw new InvalidUsageException();
            }
        } catch (IOException e) {
            System.out.println("IOException occured, bronti git folder may be corrupted.");
            e.printStackTrace();
        } catch (InvalidGitDirException | CommitException e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        } catch (InvalidUsageException e) {
            System.out.println("Invalid command.");
            e.printStackTrace();
        }
    }
}
