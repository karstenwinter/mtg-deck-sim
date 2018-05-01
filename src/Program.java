import forge.GuiBase;
import forge.GuiDesktop;
import forge.error.ExceptionHandler;
import forge.view.SimulateMatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Program {
    final static Pattern END_PATTERN = Pattern.compile(".*Game (\\d+) ended in (\\d+) ms\\. Ai\\((\\d+)\\)-(.+) has won!.*");
    final static Pattern TURN_PATTERN = Pattern.compile(".*Turn: Turn (\\d+).*");
    final static String FORGE_VERSION = "1.6.9";

    public static void main(String[] args) throws Exception {
        System.out.println("Loading decks...");
        //for (Object x : System.getenv().entrySet()) { System.out.println(x); }
        String appData = System.getenv("APPDATA");
        String os = System.getenv("OS");
        if (!"Windows_NT".equalsIgnoreCase(os)) {
            System.err.println("Cannot locate constructed deck dir for os " + os);
        } else {
            String dir = appData + "/Forge/decks/constructed";
            Stream<Path> decks = Files.list(Paths.get(dir));
            Stream<String> names = decks.map((Path x) -> x.toFile().getName());
            List<String> allDecks = names.collect(Collectors.toList());
            Random rnd = new Random();
            allDecks.sort((a, b) -> rnd.nextInt());
            System.out.println("names: " + Arrays.deepToString(allDecks.toArray()));
            //Files.readAllLines()
            //Runtime.getRuntime().exec("cd " + dir);
            System.out.println("Loading engine...");

            System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
            System.setProperty("sun.java2d.d3d", "false");
            GuiBase.setInterface(new GuiDesktop());
            ExceptionHandler.registerErrorHandling();

            int num = allDecks.size() * (allDecks.size() - 1);
            System.out.println("Matches: (total " + num + ")");
            File f = Paths.get("out.csv").toFile();
            if (!f.exists()) {
                Files.createFile(f.toPath());
                appendLineToCsv(Result.header);
            }
            for (int i = 0; i < allDecks.size(); i++) {
                for (int j = i + 1; j < allDecks.size(); j++) {
                    String a = allDecks.get(i).replace(".dck", "");
                    String b = allDecks.get(j).replace(".dck", "");
                    System.out.println("Deck " + a + " vs " + b + " (match " + (i + j) + " / " + num + ")");
                    Result res = runMatch(a, b);
                    res.deck1 = a;
                    res.deck2 = b;
                    res.hash1 = "" + a.hashCode();
                    res.hash2 = "" + b.hashCode();
                    res.version = FORGE_VERSION;
                    if (res.winner != null)
                        System.out.println("=> Winner: " + res.winner + " within " + res.timeInMs / 1000.0 + " sec on turn " + res.turns + " reason: " + res.reason);
                    else
                        System.out.println("=> Draw after: " + res.timeInMs / 1000.0 + " sec on turn " + res.turns);
                    if (!res.fails.isEmpty())
                        System.err.println(Arrays.deepToString(res.fails.toArray()));

                    appendLineToCsv(res.toString());
                }
            }
        }
    }

    static Path appendLineToCsv(String res) throws Exception {
        return Files.write(Paths.get("out.csv"), Collections.singletonList(res), StandardOpenOption.APPEND);
    }

    static class Result {
        public String winner;
        public long timeInMs;
        public long gameNumber;
        public long winnerId;
        public long turns;
        public List<String> fails = new ArrayList<>();
        public String reason;
        public String version;
        public String deck1;
        public String hash1;
        public String deck2;
        public String hash2;

        final static String header = "deck1;deck2;gameNumber;timeInMs;winnerId;winner;turns;reason;info;hash1;hash2;version";

        @Override
        public String toString() {
            String info = Arrays.deepToString(fails.toArray());
            return deck1 + ";" + deck2 + ";" + gameNumber + ";" + timeInMs + ";" + winnerId + ";" + winner + ";" + turns + ";" + f(reason) + ";" + f(info) + ";" + hash1 + ";" + hash2 + ";" + version;
        }

        private String f(String s) {
            return s.replace(';', '_').replace("\r", "").replace("\n", "");
        }
    }

    static Result runMatch(String a, String b) throws Exception {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream str = new PrintStream(bytes);
        System.setOut(str);
        long start = System.currentTimeMillis();
        try {
            String[] args = ("sim -d " + a + " " + b + " -n 1").split(" ");
            SimulateMatch.simulate(args);
        } finally {
            //str.flush();
            System.setOut(oldOut);
            String res = new String(bytes.toByteArray());

            Result r = new Result();
            for (String line : res.split("\n")) {
                if (line.contains("Stopping slow match as draw")) {
                    r.timeInMs = System.currentTimeMillis() - start;
                    r.fails.add(line);
                    return r;
                }
                if (line.contains("AI failed")) {
                    r.fails.add(line);
                }
                if (line.contains("has lost"))
                    r.reason = line;
                Matcher m = TURN_PATTERN.matcher(line);
                if (m.find()) {
                    r.turns = Long.parseLong(m.group(1));
                }
                m = END_PATTERN.matcher(line);
                if (m.find()) {
                    r.gameNumber = Long.parseLong(m.group(1));
                    r.timeInMs = Long.parseLong(m.group(2));
                    r.winnerId = Long.parseLong(m.group(3));
                    r.winner = m.group(4);
                    return r;
                }
            }
            //System.out.println("--> " + res);
            return null;
        }
    }
}
