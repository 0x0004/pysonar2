package org.yinwang.pysonar.demos;

import org.jetbrains.annotations.NotNull;
import org.yinwang.pysonar.FancyProgress;
import org.yinwang.pysonar.Indexer;
import org.yinwang.pysonar.Util;

import java.io.File;
import java.util.List;


public class Demo
{

    private static File OUTPUT_DIR;

    private static final String CSS =
            "body { color: #666666; } \n" +
                    "a {text-decoration: none; color: #5A82F7}\n" +
                    "table, th, td { border: 1px solid lightgrey; padding: 5px; corner: rounded; }\n" +
                    ".builtin {color: #B17E41;}\n" +
                    ".comment, .block-comment {color: #aaaaaa; font-style: italic;}\n" +
                    ".constant {color: #888888;}\n" +
                    ".decorator {color: #778899;}\n" +
                    ".doc-string {color: #aaaaaa;}\n" +
                    ".error {border-bottom: 1px solid red;}\n" +
                    ".field-name {color: #2e8b57;}\n" +
                    ".function {color: #4682b4;}\n" +
                    ".identifier {color: #8b7765;}\n" +
                    ".info {border-bottom: 1px dotted RoyalBlue;}\n" +
                    ".keyword {color: #0000cd;}\n" +
                    ".lineno {color: #aaaaaa;}\n" +
                    ".number {color: #483d8b;}\n" +
                    ".parameter {color: #777777;}\n" +
                    ".string {color: #999999;}\n" +
                    ".type-name {color: #4682b4;}\n" +
                    ".warning {border-bottom: 1px dotted orange;}\n";

    private static final String JS =
            "<script language=\"JavaScript\" type=\"text/javascript\">\n" +
                    "var highlighted = new Array();\n" +
                    "function highlight()\n" +
                    "{\n" +
                    "    // clear existing highlights\n" +
                    "    for (var i = 0; i < highlighted.length; i++) {\n" +
                    "        var elm = document.getElementById(highlighted[i]);\n" +
                    "        if (elm != null) {\n" +
                    "            elm.style.backgroundColor = 'white';\n" +
                    "        }\n" +
                    "    }\n" +
                    "    highlighted = new Array();\n" +
                    "    for (var i = 0; i < arguments.length; i++) {\n" +
                    "        var elm = document.getElementById(arguments[i]);\n" +
                    "        if (elm != null) {\n" +
                    "            elm.style.backgroundColor='gold';\n" +
                    "        }\n" +
                    "        highlighted.push(arguments[i]);\n" +
                    "    }\n" +
                    "} </script>\n";


    private Indexer indexer;
    private String rootPath;
    private Linker linker;


    private void makeOutputDir()
    {
        if (!OUTPUT_DIR.exists())
        {
            OUTPUT_DIR.mkdirs();
            Util.msg("Created directory: " + OUTPUT_DIR.getAbsolutePath());
        }
    }


    private void start(@NotNull File fileOrDir) throws Exception
    {
        File rootDir = fileOrDir.isFile() ? fileOrDir.getParentFile() : fileOrDir;
        try
        {
            rootPath = Util.unifyPath(rootDir);
        }
        catch (Exception e)
        {
            Util.die("File not found: " + fileOrDir);
        }

        indexer = new Indexer();
        Util.msg("Loading and analyzing files");
        indexer.loadFileRecursive(Util.unifyPath(fileOrDir));
        indexer.finish();

        generateHtml();
        indexer.close();
    }


    private void generateHtml()
    {
        Util.msg("\nGenerating HTML");
        makeOutputDir();

        linker = new Linker(rootPath, OUTPUT_DIR);
        linker.findLinks(indexer);

        int rootLength = rootPath.length();
        Util.msg("\nGenerating HTML");

        int total = 0;
        for (String path : indexer.getLoadedFiles())
        {
            if (path.startsWith(rootPath))
            {
                total++;
            }
        }

        FancyProgress progress = new FancyProgress(total, 50);

        for (String path : indexer.getLoadedFiles())
        {
            if (path.startsWith(rootPath))
            {
                progress.tick();
                File destFile = Util.joinPath(OUTPUT_DIR, path.substring(rootLength));
                destFile.getParentFile().mkdirs();
                String destPath = destFile.getAbsolutePath() + ".html";
                String html = markup(path);
                try
                {
                    Util.writeFile(destPath, html);
                }
                catch (Exception e)
                {
                    Util.msg("Failed to write: " + destPath);
                }
            }
        }

        Util.msg("\nWrote " + indexer.getLoadedFiles().size() + " files to " + OUTPUT_DIR);
    }


    @NotNull
    private String markup(String path)
    {
        String source;

        try
        {
            source = Util.readFile(path);
        }
        catch (Exception e)
        {
            Util.die("Failed to read file: " + path);
            return "";
        }

        List<StyleRun> styles = new Styler(indexer, linker).addStyles(path, source);
        styles.addAll(linker.getStyles(path));

        String styledSource = new StyleApplier(path, source, styles).apply();
        String outline = new HtmlOutline(indexer).generate(path);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head title=\"").append(path).append("\">")
                .append("<style type='text/css'>\n").append(CSS).append("</style>\n")
                .append(JS)
                .append("</head>\n<body>\n")
                .append("<table width=100% border='1px solid gray'><tr><td valign='top'>")
                .append(outline)
                .append("</td><td>")
                .append("<pre>").append(addLineNumbers(styledSource)).append("</pre>")
                .append("</td></tr></table></body></html>");
        return sb.toString();
    }


    @NotNull
    private String addLineNumbers(@NotNull String source)
    {
        StringBuilder result = new StringBuilder((int) (source.length() * 1.2));
        int count = 1;
        for (String line : source.split("\n"))
        {
            result.append("<span class='lineno'>");
            result.append(count++);
            result.append("</span> ");
            result.append(line);
            result.append("\n");
        }
        return result.toString();
    }


    private static void usage()
    {
        Util.msg("Usage:  java -jar pysonar-2.0-SNAPSHOT.jar <file-or-dir> <output-dir>");
        Util.msg("Example that generates an index for Python 2.7 standard library:");
        Util.msg(" java -jar pysonar-2.0-SNAPSHOT.jar /usr/lib/python2.7 ./html");
        System.exit(0);
    }


    @NotNull
    private static File checkFile(String path)
    {
        File f = new File(path);
        if (!f.canRead())
        {
            Util.die("Path not found or not readable: " + path);
        }
        return f;
    }


    public static void main(@NotNull String[] args) throws Exception
    {
        if (args.length != 2)
        {
            usage();
        }

        File fileOrDir = checkFile(args[0]);
        OUTPUT_DIR = new File(args[1]);

        new Demo().start(fileOrDir);

        Util.msg(Util.getGCStats());

    }
}
