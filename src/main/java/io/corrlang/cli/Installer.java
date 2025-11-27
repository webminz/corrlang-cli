package io.corrlang.cli;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Logic for downloading and installing CorrLang versions.
 */
public class Installer {

    private static final String BASE_DOWNLOAD_LINK = "https://codeberg.org/drstrudel/corrlang/releases/download//";

    // TODO: make configurable in the future
    private static final String CURRENT_RELEASE_NAME = "1.0-snapshot-pre-alpha";

    public static final String CURRENT_VERSION = "corrlang-1.0-snapshot";
    private static final String CORRLANG_ARCHIVE_NAME = CURRENT_VERSION + ".zip";
    public static final String CONFIG_FILE_NAME = "config.toml";

    private static class DeleteFileVisitor implements FileVisitor<Path> {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.TERMINATE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }
    }


    public static void downloadAndUnpackZip(File targetDirectory, boolean overwrite, int port) throws IOException, URISyntaxException {
        // Check if target directory exists and contains files
        if (targetDirectory.exists()) {
            List<String> directoryContents = Arrays.asList(Objects.requireNonNull(targetDirectory.list()));
            if (directoryContents.contains("lib")) {
                if (overwrite) {
                    Files.walkFileTree(Path.of(targetDirectory.getAbsolutePath(), "lib"), new DeleteFileVisitor());
                    System.out.println("INFO: Overwriting existing CorrLang installation in the target directory: '" + targetDirectory.getAbsolutePath() + "/lib'");
                } else {
                    System.out.println("ERROR: CorrLang installation already exists in the target directory: " + targetDirectory.getAbsolutePath());
                    return;
                }
            }
            if (directoryContents.contains("bin")) {
                if (overwrite) {
                    Files.walkFileTree(Path.of(targetDirectory.getAbsolutePath(), "bin"), new DeleteFileVisitor());
                    System.out.println("INFO: Overwriting existing CorrLang installation in the target directory: '" + targetDirectory.getAbsolutePath() + "/bin'");
                } else {
                    System.out.println("ERROR: CorrLang installation already exists in the target directory: " + targetDirectory.getAbsolutePath());
                    return;
                }
            }
        } else {
            targetDirectory.mkdirs();
        }

        File configFile = new File(targetDirectory, CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            writeDefaultConfig(configFile, port, targetDirectory);
            System.out.println("INFO: Created default configuration file at: " + configFile.getAbsolutePath());
        }

        // Step 1: Download the ZIP file
        InputStream inputStream = new URI(BASE_DOWNLOAD_LINK + "/" + CURRENT_RELEASE_NAME + "/" + CORRLANG_ARCHIVE_NAME).toURL().openStream();
        Path tempZipPath = Files.createTempFile("corrlang.downloaded", ".zip");

        System.out.print("Downloading CorrLang version '" + CURRENT_RELEASE_NAME + "'...");
        try (BufferedInputStream bis = new BufferedInputStream(inputStream);
             FileOutputStream fos = new FileOutputStream(tempZipPath.toFile())) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("done.");
        System.out.println("Unpacking distribution to directory: " + targetDirectory.getAbsolutePath());

        // Step 2: Unpack the ZIP file
        unpackZip(tempZipPath.toFile(), targetDirectory);

        // Clean up: Delete the temporary ZIP file
        Files.delete(tempZipPath);

        System.out.println("CorrLang installed successfully!");
    }

    private static void unpackZip(File zipFilePath, File destDir) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                String filePath = destDir + File.separator + entry.getName();
                if (entry.getName().startsWith(CURRENT_VERSION)) {
                    filePath = destDir + File.separator + entry.getName().substring(CURRENT_VERSION.length() + 1);
                }
                if (!entry.isDirectory()) {
                    // If the entry is a file, extract it
                    extractFile(zipIn, filePath);
                } else {
                    // If the entry is a directory, make the directory
                    File dirEntry = new File(filePath);
                    dirEntry.mkdirs();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    private static void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath))) {
            byte[] bytesIn = new byte[1024];
            int read;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
        }
    }

    private static void writeDefaultConfig(File file, int port, File corrlangHome) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file);
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos));
        InputStream inputStream = Installer.class.getResourceAsStream("/config.template.toml")) {
            writer.write("[system]\n");
            writer.write("port = " + port + "\n");
            writer.write("home = \"" + corrlangHome.getAbsolutePath() +"\"\n");
            writer.write("\n");
            writer.flush();
            Objects.requireNonNull(inputStream).transferTo(fos);
        }
    }

}
