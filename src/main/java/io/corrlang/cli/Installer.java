package io.corrlang.cli;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Logic for downloading and installing CorrLang versions.
 */
public class Installer {

    private static final String BASE_DOWNLOAD_LINK = "https://codeberg.org/drstrudel/corrlang/releases/download/";
    private static final String CORRLANG_ARCHIVE_NAME = "corrlang.zip";
    public static final String CONFIG_FILE_NAME = "config.toml";

    public static Optional<String> getInstalledVersion(Path corrlangHome) throws Exception {
        Path libDir = corrlangHome.resolve("lib");
        if (Files.exists(libDir) && Files.isDirectory(libDir) ) {
            try (Stream<Path> f = Files.list(libDir)) {
                List<Path> list = f.toList();
                Optional<String> corrLangVersion = list.stream()
                        .map(libDir::relativize)
                        .map(path -> path.getFileName().toString())
                        .filter(path -> path.endsWith(".jar"))
                        .filter(path -> path.startsWith("corrlang-service-"))
                        .map(path -> path.substring(17, path.length() - 4))
                        .findFirst();
                if (corrLangVersion.isPresent()) {
                    return corrLangVersion;
                } else {
                    throw new Exception("CorrLang installation at '" + corrlangHome.toAbsolutePath().toString() + "' seems corrupted: could not find 'corrlang-service-<version>.jar' in the 'lib' directory!");
                }
            }
        } else {
            return Optional.empty();
        }
    }


    public static Dto.CorrLangInstalled downloadAndUnpackZip(
            Path targetDirectory,
            String version,
            boolean overwrite,
            int port) throws Exception {
        // Check if target directory exists and contains files

        boolean hasOverwritten = false;

        if (Files.exists(targetDirectory)) {
            Stream<Path> pathStream = Files.list(targetDirectory);
            List<String> directoryContents = pathStream.map(path -> path.getFileName().toString()).toList();
            pathStream.close();
            if (directoryContents.contains("lib") || directoryContents.contains("bin")) {
                if (overwrite) {
                    hasOverwritten = true;
                    System.out.println(" - Found existing CorrLang installation at '" + targetDirectory.toAbsolutePath() + "', overwriting as per user request.");
                    Files.walkFileTree(targetDirectory.resolve("lib"), DeleteFileVisitor.getInstance());
                    Files.walkFileTree(targetDirectory.resolve("bin"), DeleteFileVisitor.getInstance());
                } else {
                    throw new Exception("CorrLang installation already exists in the target directory: '" + targetDirectory.toAbsolutePath() + "'! Use the '--overwrite' flag to overwrite the existing installation.");
                }
            }

        } else {
            System.out.println(" - Target directory is empty, creating directory structure at: '" + targetDirectory.toAbsolutePath() + "'.");
            Files.createDirectories(targetDirectory);
        }

        Path configFile = targetDirectory.resolve(CONFIG_FILE_NAME);
        if (!Files.exists(configFile)) {
            writeDefaultConfig(configFile.toFile(), port, targetDirectory.toFile());
            System.out.println(" - Created new default configuration file at: '" + configFile.toAbsolutePath()  + "'.");
        } else  {
            System.out.println(" - Found existing configuration file at: '" + configFile.toAbsolutePath() + "'.");
        }

        // Step 1: Download the ZIP file
        InputStream inputStream = new URI(BASE_DOWNLOAD_LINK + "/" + version + "/" + CORRLANG_ARCHIVE_NAME).toURL().openStream();
        Path tempZipPath = Files.createTempFile("corrlang.downloaded", ".zip");

        System.out.print(" - Downloading CorrLang version '" + version + "'...");
        try (BufferedInputStream bis = new BufferedInputStream(inputStream);
             FileOutputStream fos = new FileOutputStream(tempZipPath.toFile())) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
        System.out.println(" ...done.");
        System.out.println(" - Unpacking distribution to directory: '" + targetDirectory.toAbsolutePath() + "'.");

        // Step 2: Unpack the ZIP file
        unpackZip(tempZipPath.toFile(), targetDirectory.toFile());

        // Clean up: Delete the temporary ZIP file
        Files.delete(tempZipPath);

        return new Dto.CorrLangInstalled(
                version,
                targetDirectory.toAbsolutePath().toString(),
                hasOverwritten
        );
    }

    private static void unpackZip(File zipFilePath, File destDir) throws IOException {
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                String filePath = destDir + File.separator + entry.getName();
                if (entry.getName().startsWith("corrlang")) {
                    filePath = destDir + File.separator + entry.getName().substring(9);
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
