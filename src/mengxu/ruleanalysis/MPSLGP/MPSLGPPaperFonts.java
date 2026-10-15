package mengxu.ruleanalysis.MPSLGP;

import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.FontMapper;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static mengxu.algorithm.multiobjective.MPSLGP.PaperExperimentFiles.fileHash;

/** Loads the manuscript's Latin Modern Roman outlines for both Java2D and embedded PDFs. */
final class MPSLGPPaperFonts {
    final Font panel, label, legend, tick;
    final FontMapper mapper;
    private final List<Path> files;

    private MPSLGPPaperFonts(List<Path> files) throws IOException {
        this.files = files;
        try {
            Font regular = Font.createFont(Font.TYPE1_FONT, files.get(0).toFile());
            Font bold = Font.createFont(Font.TYPE1_FONT, files.get(2).toFile());
            BaseFont regularPdf = BaseFont.createFont(files.get(1).toString(), BaseFont.CP1252, BaseFont.EMBEDDED,
                    false, Files.readAllBytes(files.get(1)), Files.readAllBytes(files.get(0)));
            BaseFont boldPdf = BaseFont.createFont(files.get(3).toString(), BaseFont.CP1252, BaseFont.EMBEDDED,
                    false, Files.readAllBytes(files.get(3)), Files.readAllBytes(files.get(2)));
            if (!regular.getPSName().equals(regularPdf.getPostscriptFontName())
                    || !bold.getPSName().equals(boldPdf.getPostscriptFontName())) {
                throw new IOException("Java2D and PDF font files must describe the same Latin Modern Roman faces.");
            }
            panel = bold.deriveFont(Font.BOLD, 25f);
            label = regular.deriveFont(Font.PLAIN, 27f);
            legend = regular.deriveFont(Font.PLAIN, 23f);
            tick = regular.deriveFont(Font.PLAIN, 23f);
            mapper = new FontMapper() {
                @Override
                public BaseFont awtToPdf(Font font) { return font.isBold() ? boldPdf : regularPdf; }
                @Override
                public Font pdfToAwt(BaseFont font, int size) {
                    return font.getPostscriptFontName().equals(boldPdf.getPostscriptFontName())
                            ? bold.deriveFont(Font.BOLD, size) : regular.deriveFont(Font.PLAIN, size);
                }
            };
        } catch (FontFormatException | DocumentException e) {
            throw new IOException("Cannot load the manuscript's Latin Modern Roman font files.", e);
        }
    }

    static MPSLGPPaperFonts load() throws IOException {
        List<String> names = Arrays.asList("lmr10.pfb", "lmr10.afm", "lmbx10.pfb", "lmbx10.afm");
        List<Path> paths = new ArrayList<>();
        String configured = System.getProperty("mpslgp.fontDirectory");
        if (configured != null) {
            Path directory = Paths.get(configured).toAbsolutePath().normalize();
            for (String name : names) { paths.add(directory.resolve(name)); }
        } else {
            List<String> command = new ArrayList<>();
            command.add(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "kpsewhich.exe" : "kpsewhich");
            command.addAll(names);
            Process process;
            try { process = new ProcessBuilder(command).redirectErrorStream(true).start(); }
            catch (IOException e) {
                throw new IOException("Latin Modern Roman requires TeX Live/MiKTeX kpsewhich on PATH, or "
                        + "-Dmpslgp.fontDirectory=<directory containing lmr10/lmbx10 .pfb and .afm files>.", e);
            }
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    throw new IOException("Timed out locating the manuscript's Latin Modern Roman fonts.");
                }
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (process.exitValue() != 0 || output.isEmpty()) {
                    throw new IOException("kpsewhich could not locate all four Latin Modern Roman font files: " + output);
                }
                for (String line : output.split("\\R")) { paths.add(Paths.get(line.trim()).toAbsolutePath().normalize()); }
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while locating Latin Modern Roman fonts.", e);
            } finally { process.getInputStream().close(); }
        }
        if (paths.size() != names.size()) { throw new IOException("Expected four Latin Modern Roman font paths; found " + paths.size()); }
        for (int i = 0; i < paths.size(); i++) {
            if (!Files.isRegularFile(paths.get(i)) || !paths.get(i).getFileName().toString().equals(names.get(i))) {
                throw new IOException("Missing or incorrect manuscript font file: " + paths.get(i));
            }
        }
        return new MPSLGPPaperFonts(paths);
    }

    Map<String, String> provenance() throws IOException {
        Map<String, String> result = new TreeMap<>();
        result.put("figure.font.family", "Latin Modern Roman 10, regular and bold; same family as manuscript lmodern");
        for (Path file : files) {
            result.put("figure.font." + file.getFileName() + ".path", file.toString());
            result.put("figure.font." + file.getFileName() + ".sha256", fileHash(file));
        }
        result.put("figure.pdf.fonts", "Embedded Type 1 Latin Modern Roman; identical outlines to Java2D PNG rendering");
        return result;
    }
}
