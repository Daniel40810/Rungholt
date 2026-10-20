import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Isolationsprüfung der Element-Packages (Paketvertrag Punkt 9, Ant-Target {@code check-packages}).
 * <p>
 * Jedes Package unter {@code src/com/dan/} außer der App {@code rungholt} wird für sich allein übersetzt, ohne die
 * übrigen Quellen:
 * <ol>
 * <li><b>Kern</b> (alles außer {@code gl/} und {@code demo/}) nur gegen das JDK,</li>
 * <li><b>Kern + gl/</b> zusätzlich gegen JOGL,</li>
 * <li><b>alles + demo/</b> zusätzlich gegen FStyle.</li>
 * </ol>
 * Dazu liest es die Importe und voll qualifizierten Namen und meldet alles außer {@code java.*}, {@code javax.*}, dem
 * eigenen Package (und JOGL bzw. FStyle, wo erlaubt), und es prüft, ob jede Shader-Datei, die {@code gl/} nennt, im
 * Package liegt. Rot heißt: nicht fertig. Aufruf ohne Ant: {@code java tools/PaketPruefung.java src lib}.
 */
public final class PaketPruefung {
    static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE),
            FQN = Pattern.compile("\\bcom\\.dan\\.[\\w.]*\\w"),
            SHADER = Pattern.compile("\"([\\w./-]+\\.(?:vert|frag|comp|geom|glsl))\"");

    public static void main(String[] args) throws Exception {
        File src = new File(args.length > 0 ? args[0] : "src"), lib = new File(args.length > 1 ? args[1] : "lib");
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            System.err.println("check-packages: kein Java-Compiler gefunden (läuft Ant mit einem JRE statt einem JDK?)");
            System.exit(2);
        }
        String jogl = jars(lib, "jogl-all.jar", "gluegen-rt.jar"), fstyle = jars(lib, "FStyle.jar");
        // Phase 13: erlaubt sind nur Packages, die wirklich in FStyle.jar liegen (com.dan.fog und com.dan.flock beginnen
        // auch mit „com.dan.f“ und dürfen von anderen Demos nicht genommen werden)
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(fstyle)) {
            z.stream().map(java.util.zip.ZipEntry::getName).filter(n -> n.endsWith(".class") && n.lastIndexOf('/') > 0)
                    .forEach(n -> FSTYLE.add(n.substring(0, n.lastIndexOf('/')).replace('/', '.')));
        }
        File base = new File(src, "com/dan");
        File[] dirs = base.listFiles(f -> f.isDirectory() && !f.getName().equals("rungholt"));
        if (dirs == null || dirs.length == 0) {
            System.out.println("check-packages: keine Element-Packages unter " + base);
            return;
        }
        java.util.Arrays.sort(dirs, Comparator.comparing(File::getName));
        System.out.println("check-packages: " + dirs.length + " Packages, jedes für sich übersetzt");
        System.out.printf("  %-14s %-6s %-6s %-6s %-8s %s%n", "Package", "Kern", "gl", "demo", "Importe", "Dateien");
        int failed = 0;
        List<String> reports = new ArrayList<>();
        for (File d : dirs) {
            String name = d.getName(), own = "com.dan." + name;
            List<File> core = new ArrayList<>(), gl = new ArrayList<>(), demo = new ArrayList<>();
            try (Stream<Path> s = Files.walk(d.toPath())) {
                s.filter(p -> p.toString().endsWith(".java")).sorted().forEach(p -> {
                    String rel = d.toPath().relativize(p).toString().replace('\\', '/');
                    (rel.startsWith("gl/") ? gl : rel.startsWith("demo/") ? demo : core).add(p.toFile());
                });
            }
            String rCore = compile(javac, core, "", name + " Kern", reports);
            List<File> cg = new ArrayList<>(core);
            cg.addAll(gl);
            String rGl = gl.isEmpty() ? "–" : compile(javac, cg, jogl, name + " gl", reports);
            List<File> all = new ArrayList<>(cg);
            all.addAll(demo);
            String rDemo = demo.isEmpty() ? "–" : compile(javac, all, join(jogl, fstyle), name + " demo", reports);
            List<String> bad = new ArrayList<>();
            for (File f : core) bad.addAll(scan(f, own, false, false, d));
            for (File f : gl) bad.addAll(scan(f, own, true, false, d));
            for (File f : demo) bad.addAll(scan(f, own, true, true, d));
            for (File f : gl) bad.addAll(shaders(f));
            String rImp = bad.isEmpty() ? "ok" : "FEHLER";
            if (!bad.isEmpty()) reports.add(name + " Importe/Ressourcen:\n    " + String.join("\n    ", bad));
            boolean good = !rCore.equals("FEHLER") && !rGl.equals("FEHLER") && !rDemo.equals("FEHLER") && bad.isEmpty();
            if (!good) failed++;
            System.out.printf("  %-14s %-6s %-6s %-6s %-8s %d%n", name, rCore, rGl, rDemo, rImp, core.size() + gl.size() + demo.size());
        }
        for (String r : reports) System.out.println("\n" + r);
        if (failed > 0) {
            System.out.println("\ncheck-packages: " + failed + " von " + dirs.length + " Packages sind nicht unabhängig.");
            System.exit(1);
        }
        System.out.println("check-packages: alle " + dirs.length + " Packages unabhängig.");
    }

    static final java.util.Set<String> FSTYLE = new java.util.HashSet<>();

    /** Gehört der Name (Import oder voll qualifiziert) zu einem Package aus FStyle.jar? */
    static boolean fstyle(String name) {
        for (String p = name; p.contains("."); p = p.substring(0, p.lastIndexOf('.'))) if (FSTYLE.contains(p)) return true;
        return false;
    }

    static String jars(File lib, String... names) {
        List<String> l = new ArrayList<>();
        for (String n : names) {
            File f = new File(lib, n);
            if (!f.isFile()) {
                System.err.println("check-packages: " + f + " fehlt");
                System.exit(2);
            }
            l.add(f.getAbsolutePath());
        }
        return String.join(File.pathSeparator, l);
    }

    static String join(String a, String b) { return a.isEmpty() ? b : b.isEmpty() ? a : a + File.pathSeparator + b; }

    /** Übersetzt die Dateien allein in einen leeren Ordner; „ok“ oder „FEHLER“ (Meldungen in reports). */
    static String compile(JavaCompiler javac, List<File> files, String cp, String what, List<String> reports) throws IOException {
        if (files.isEmpty()) return "–";
        Path out = Files.createTempDirectory("paketpruefung");
        try {
            DiagnosticCollector<JavaFileObject> diag = new DiagnosticCollector<>();
            try (StandardJavaFileManager fm = javac.getStandardFileManager(diag, Locale.GERMAN, StandardCharsets.UTF_8)) {
                List<String> opt = new ArrayList<>(List.of("--release", "21", "-encoding", "UTF-8", "-proc:none", "-nowarn", "-implicit:none",
                        "-d", out.toString(), "-sourcepath", ""));
                opt.add("-classpath");
                opt.add(cp);
                boolean ok = javac.getTask(null, fm, diag, opt, null, fm.getJavaFileObjectsFromFiles(files)).call();
                if (ok) return "ok";
                StringBuilder sb = new StringBuilder(what + ":");
                int n = 0;
                for (Diagnostic<? extends JavaFileObject> d : diag.getDiagnostics()) {
                    if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                    if (n++ == 6) { sb.append("\n    …"); break; }
                    String file = d.getSource() == null ? "" : new File(d.getSource().toUri()).getName() + ":" + d.getLineNumber() + " ";
                    sb.append("\n    ").append(file).append(d.getMessage(Locale.GERMAN).split("\n")[0]);
                }
                reports.add(sb.toString());
                return "FEHLER";
            }
        } finally {
            try (Stream<Path> s = Files.walk(out)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** Importe und voll qualifizierte Namen außerhalb des Erlaubten (Kommentare zählen nicht). */
    static List<String> scan(File f, String own, boolean jogl, boolean fstyle, File pkgDir) throws IOException {
        List<String> bad = new ArrayList<>();
        String src = Files.readString(f.toPath(), StandardCharsets.UTF_8);
        String rel = pkgDir.toPath().relativize(f.toPath()).toString().replace('\\', '/');
        Matcher m = IMPORT.matcher(src);
        while (m.find()) {
            String imp = m.group(2);
            if (imp.startsWith("java.") || imp.startsWith("javax.") || imp.equals(own) || imp.startsWith(own + ".")) continue;
            if (jogl && imp.startsWith("com.jogamp.")) continue;
            if (fstyle && fstyle(imp)) continue;
            bad.add(rel + ": import " + imp);
        }
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "").replaceAll("(?m)^\\s*(package|import)\\s.*$", "");
        Matcher q = FQN.matcher(code);
        while (q.find()) {
            String n = q.group();
            if (n.equals(own) || n.startsWith(own + ".") || (fstyle && fstyle(n))) continue;
            bad.add(rel + ": " + n);
        }
        return bad;
    }

    /**
     * Ein Pfad mit führendem / (Klassenpfad, etwa „/com/dan/mudflat/gl/mudflat.glsl“) zählt nur, wenn er im eigenen
     * Package liegt; sonst null.
     */
    static File absolute(File f, String n) {
        File d = f.getParentFile();
        while (d != null && !(d.getName().equals("dan") && d.getParentFile() != null && d.getParentFile().getName().equals("com"))) {
            if (d.getParentFile() != null && d.getParentFile().getName().equals("dan") && d.getParentFile().getParentFile() != null
                    && d.getParentFile().getParentFile().getName().equals("com")) {
                String own = "/com/dan/" + d.getName() + "/";
                File root = d.getParentFile().getParentFile().getParentFile();
                return n.startsWith(own) ? new File(root, n.substring(1)) : null;
            }
            d = d.getParentFile();
        }
        return null;
    }

    /** Jede Shader-Datei, die eine Klasse in gl/ beim Namen nennt, muss neben ihr liegen (oder im eigenen Package). */
    static List<String> shaders(File f) throws IOException {
        List<String> bad = new ArrayList<>();
        Matcher m = SHADER.matcher(Files.readString(f.toPath(), StandardCharsets.UTF_8));
        while (m.find()) {
            String n = m.group(1);
            File r = n.startsWith("/") ? absolute(f, n) : new File(f.getParentFile(), n);
            if (r == null || !r.isFile()) bad.add(f.getName() + ": Shader " + n + " liegt nicht im Package");
        }
        return bad;
    }
}
