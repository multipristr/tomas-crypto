import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Scanner;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
    private static final double FEE = 0.999;
    private static final double FIAT = 100_000.0;
    private static final short STEPS = 3500;
    private static final String BUY_DAY = "-09";
    private static final byte TRADES_LIMIT = 3;
    private static final String CUT_OFF_DATE = "2017-12-13";

    public static void main(String[] args) throws IOException {
        String[] coinNames = determineCoinNames(args);

        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("..", "..", "..", "public", "index.html"))) {
            writer.write("<!DOCTYPE html>\n" +
                    "<html lang=\"en\">\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\">\n" +
                    "    <title>Tomas' crypto</title>\n" +
                    "    <meta content=\"Tomas' crypto\" name=\"title\">\n" +
                    "    <meta content=\"summary\" name=\"twitter:card\">\n" +
                    "    <meta content=\"website\" property=\"og:type\">\n" +
                    "    <meta content=\"https://tomas-crypto.gitlab.io/coins/\" property=\"og:url\">\n" +
                    "    <meta content=\"Tomas' crypto\" property=\"og:title\">\n" +
                    "    <meta content=\"\" property=\"og:determiner\">\n" +
                    "    <meta content=\"en_US\" property=\"og:locale\">\n" +
                    "    <meta content=\"https://tomas-crypto.gitlab.io/coins/assets/favicon/android-chrome-512x512.png\" property=\"og:image\">\n" +
                    "    <meta content=\"https://tomas-crypto.gitlab.io/coins/assets/favicon/android-chrome-512x512.png\" property=\"og:image:secure_url\">\n" +
                    "    <meta content=\"image/png\" property=\"og:image:type\">\n" +
                    "    <meta content=\"512\" property=\"og:image:width\">\n" +
                    "    <meta content=\"512\" property=\"og:image:height\">\n" +
                    "    <meta content=\"width=device-width, initial-scale=1\" name=\"viewport\">\n" +
                    "    <link href=\"assets/apple-touch-icon.png\" rel=\"apple-touch-icon\" sizes=\"180x180\">\n" +
                    "    <link href=\"assets/favicon-32x32.png\" rel=\"icon\" sizes=\"32x32\" type=\"image/png\">\n" +
                    "    <link href=\"assets/favicon-16x16.png\" rel=\"icon\" sizes=\"16x16\" type=\"image/png\">\n" +
                    "    <link href=\"assets/site.webmanifest\" rel=\"manifest\">\n" +
                    "    <link href=\"assets/favicon.ico\" rel=\"icon\">\n" +
                    "    <link href=\"style.css\" rel=\"stylesheet\" type=\"text/css\">\n" +
                    "</head>" +
                    "<body>\n" +
                    "<pre>\n");
            loadCoins(coinNames)
                    .filter(coin -> CUT_OFF_DATE.compareTo(coin.data[0].date) >= 0)
                    .map(Main::staticValueInvesting)
                    .sorted(Comparator.comparingDouble(result -> result.gain))
                    .forEachOrdered(pair -> {
                        System.out.println(pair.transactions);
                        System.out.println();
                        try {
                            writer.write(pair.transactions);
                            writer.newLine();
                            writer.newLine();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
            writer.write("</pre>\n" +
                    "<footer>\n" +
                    "<a href=\"https://www.flaticon.com/free-icons/cryptocurrency\" title=\"cryptocurrency icons\">Cryptocurrency icons created by Smashicons - Flaticon</a>\n" +
                    "</footer>\n" +
                    "</body>\n" +
                    "</html>\n");
        }

		/*Arrays.stream(coins)
				.map(coin -> new AbstractMap.SimpleImmutableEntry<>(coin.name, profitSellInvesting(coin)))
				.sorted(Map.Entry.comparingByValue())
				.forEach(pair -> System.out.println(pair.getKey() + ' ' + pair.getValue()));*/

		/*Arrays.stream(coins)
				.map(coin -> new AbstractMap.SimpleImmutableEntry<>(coin.name, staticValueInvesting(coin)))
				.sorted(Map.Entry.comparingByValue())
				.forEach(pair -> System.out.println(pair.getKey() + ' ' + pair.getValue()));*/

		/*Arrays.stream(coins)
				.map(coin -> new AbstractMap.SimpleImmutableEntry<>(coin.name, multipleCheapInvesting(coin)))
				.sorted(Map.Entry.comparingByValue())
				.forEach(pair -> System.out.println(pair.getKey() + ' ' + pair.getValue()));*/
    }

    private static String[] determineCoinNames(String[] args) {
        String[] coinNames = new String[]{
                "SOL", "MNT27075", "KAS", "VET", "FTM", "RUNE", "THETA", "OKB", "OP", "PEPE24478",
                "IMX10603", "HBAR", "CRO", "GRT6719", "INJ", "DAI", "STX4847", "ARB11841", "ATOM", "RNDR",
                "TAO22974", "DOGE", "AVAX", "TON", "DOT", "MATIC", "UNI7083", "ICP", "NEAR", "APT21794", "LEO",
                "BTC", "ETH", "BNB", "ADA", "XRP", "LTC", "LINK", "BCH", "XLM", "TRX", "ETC", "FIL", "XMR"
        };
        if (args.length > 0) {
            String[] argumentCoinNames = Arrays.stream(args[0].split(","))
                    .map(String::trim)
                    .filter(ticker -> !ticker.isEmpty())
                    .toArray(String[]::new);
            if (argumentCoinNames.length < 1) {
                System.err.println("Ignoring coin names from program arguments '" + args[0] + '\'');
            } else {
                coinNames = argumentCoinNames;
                System.out.println("Coin names " + Arrays.stream(argumentCoinNames)
                        .map(proposal -> '\'' + proposal + '\'')
                        .collect(Collectors.joining(", "))
                );
            }
        }
        return coinNames;
    }

    private static double profitSellInvesting(Coin coin) {
        double bestSell = 0.0;
        double bestGain = Double.NEGATIVE_INFINITY;
        for (double sellTrigger = 3.0; sellTrigger < 60; sellTrigger += 0.2) {
            double coins = 0;
            double cash = FIAT;
            double invested = cash;
            for (Day day : coin.data) {
                double gain = (coins * day.high * FEE) / invested;
                if (gain >= sellTrigger) {
                    cash += coins * day.high * FEE;
                    coins = 0;
                }
                if (day.date.endsWith(BUY_DAY)) {
                    coins += ((cash + FIAT) * FEE) / day.high;
                    cash = 0;
                    invested += FIAT;
                }
            }
            cash += coin.data[coin.data.length - 1].close * coins * FEE;
            double gain = cash / invested;
            if (gain > bestGain) {
                bestGain = gain;
                bestSell = sellTrigger;
            } else if (gain == bestGain && sellTrigger < bestSell) {
                bestSell = sellTrigger;
            }
        }

        System.out.println(coin.name + " gain " + bestGain + " sell " + bestSell);
        double coins = 0;
        double cash = FIAT;
        double invested = cash;
        for (Day day : coin.data) {
            double gain = (coins * day.high * FEE) / invested;
            if (gain >= bestSell) {
                cash += coins * day.high * FEE;
                coins = 0;
                System.out.println("Sell " + day.date + ' ' + day.high);
            }
            if (day.date.endsWith(BUY_DAY)) {
                coins += ((cash + FIAT) * FEE) / day.high;
                cash = 0;
                invested += FIAT;
            }
        }
        System.out.println("Next sell " + (bestSell * invested) / (coins * FEE));
        System.out.println();

        return bestGain;
    }

    private static Result staticValueInvesting(Coin coin) {
        double max = Double.NEGATIVE_INFINITY;
        double min = Double.POSITIVE_INFINITY;
        for (Day day : coin.data) {
            max = Math.max(max, day.high);
            min = Math.min(min, day.low);
        }
        double step = (max - min) / STEPS;

        AtomicReference<Double> atomicBestBuy = new AtomicReference<>(Double.POSITIVE_INFINITY);
        AtomicReference<Double> atomicBestSell = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        AtomicReference<Double> atomicBestGain = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        double finalMin = min;
        double finalMax = max;
        IntStream.range(0, STEPS).parallel().forEach(i -> {
            double buy = finalMin + i * step;
            for (double sell = buy; sell <= finalMax; sell += step) {
                byte trades = 0;
                double coins = 0;
                double cash = FIAT;
                double invested = cash;
                double smallestSell = Double.POSITIVE_INFINITY;
                double biggestBuy = Double.NEGATIVE_INFINITY;
                for (Day day : coin.data) {
                    if (day.low <= buy && cash > 0) {
                        coins += (cash * FEE) / buy;
                        cash = 0;
                        biggestBuy = Math.max(biggestBuy, day.low);
                    } else if (day.high >= sell && coins > 0) {
                        cash += sell * coins * FEE;
                        coins = 0;
                        smallestSell = Math.min(smallestSell, day.high);
                        ++trades;
                    }
                    if (day.date.endsWith(BUY_DAY)) {
                        if (((day.high + day.low) / 2) >= sell) {
                            cash += FIAT;
                        } else {
                            coins += ((FIAT + cash) * FEE) / ((day.high + day.low) / 2);
                            cash = 0;
                            //coins += (FIAT * FEE) / ((day.high + day.low) / 2);
							/*double halfFiat = FIAT / 2;
							coins += (halfFiat * FEE) / ((day.high + day.low) / 2);
							cash += halfFiat;*/
                        }
                        invested += FIAT;
                    }
                }
                if (trades < TRADES_LIMIT) {
                    continue;
                }
                cash += coin.data[coin.data.length - 1].close * coins * FEE;
                double gain = cash / invested;
                if (gain > atomicBestGain.get()) {
                    atomicBestGain.set(gain);
                    atomicBestBuy.set(biggestBuy);
                    atomicBestSell.set(smallestSell);
                } else if (gain == atomicBestGain.get()) {
                    if (biggestBuy > atomicBestBuy.get() || (biggestBuy == atomicBestBuy.get() && smallestSell < atomicBestSell.get())) {
                        atomicBestBuy.set(biggestBuy);
                        atomicBestSell.set(smallestSell);
                    }
                }
            }
        });

        double bestSell = atomicBestSell.get();
        double bestBuy = atomicBestBuy.get();
        StringJoiner transactions = new StringJoiner(System.lineSeparator());
        transactions.add(coin.name + " gain " + atomicBestGain.get() + " buy " + bestBuy + " sell " + bestSell);
        double coins = 0;
        double cash = FIAT;
        for (Day day : coin.data) {
            if (day.low <= bestBuy && cash > 0) {
                coins += (cash * FEE) / bestBuy;
                cash = 0;
                transactions.add("Buy " + day.date + ' ' + day.low);
            } else if (day.high >= bestSell && coins > 0) {
                cash += bestSell * coins * FEE;
                coins = 0;
                transactions.add("Sell " + day.date + ' ' + day.high);
            }
            if (day.date.endsWith(BUY_DAY)) {
                if (((day.high + day.low) / 2) >= bestSell) {
                    cash += FIAT;
                } else {
                    coins += ((FIAT + cash) * FEE) / ((day.high + day.low) / 2);
                    cash = 0;
                    // coins += (FIAT * FEE) / ((day.high + day.low) / 2);
                }
            }
        }

        return new Result(atomicBestGain.get(), transactions.toString());
    }

    private static double multipleCheapInvesting(Coin coin) {
        double max = Arrays.stream(coin.data).mapToDouble(day -> day.high).max().getAsDouble();
        double min = Arrays.stream(coin.data).mapToDouble(day -> day.low).min().getAsDouble();
        double step = (max - min) / STEPS;

        double bestBuy = Double.POSITIVE_INFINITY;
        double bestSell = Double.NEGATIVE_INFINITY;
        double bestGain = Double.NEGATIVE_INFINITY;
        for (double buy = min; buy < max; buy += step) {
            for (double sell = buy; sell <= max; sell += step) {
                byte trades = 0;
                double coins = 0;
                double cash = FIAT * 6;
                double invested = cash;
                double smallestSell = Double.POSITIVE_INFINITY;
                double biggestBuy = Double.NEGATIVE_INFINITY;
                for (Day day : coin.data) {
                    if (day.low <= buy && cash > 0) {
                        coins += (FIAT * FEE) / buy;
                        cash -= FIAT;
                        biggestBuy = Math.max(biggestBuy, day.low);
                        invested += FIAT;
                    } else if (day.high >= sell && coins > 0) {
                        cash += sell * coins * FEE;
                        coins = 0;
                        smallestSell = Math.min(smallestSell, day.high);
                        ++trades;
                    }
                    if (day.date.endsWith(BUY_DAY)) {
                        cash += FIAT;
                        invested += FIAT;
                    }
                }
                if (trades < TRADES_LIMIT) {
                    continue;
                }
                cash += coin.data[coin.data.length - 1].close * coins * FEE;
                double gain = cash / invested;
                if (gain > bestGain) {
                    bestGain = gain;
                    bestBuy = biggestBuy;
                    bestSell = smallestSell;
                } else if (gain == bestGain) {
                    if (biggestBuy > bestBuy || (biggestBuy == bestBuy && smallestSell < bestSell)) {
                        bestBuy = biggestBuy;
                        bestSell = smallestSell;
                    }
                }
            }
        }

        System.out.println(coin.name + " gain " + bestGain + " buy " + bestBuy + " sell " + bestSell);
        double coins = 0;
        double cash = FIAT * 6;
        for (Day day : coin.data) {
            if (day.low <= bestBuy && cash > 0) {
                coins += (Math.min(cash, FIAT) * FEE) / bestBuy;
                cash -= FIAT;
                cash = Math.max(cash, 0);
                System.out.println("Buy " + day.date + ' ' + day.low);
            } else if (day.high >= bestSell && coins > 0) {
                cash += bestSell * coins * FEE;
                coins = 0;
                System.out.println("Sell " + day.date + ' ' + day.high);
            }
            if (day.date.endsWith(BUY_DAY)) {
                cash += FIAT;
            }
        }
        System.out.println();

        return bestGain;
    }

    private static Stream<Coin> loadCoins(String[] coinNames) {
        Pattern timestampPattern = Pattern.compile("\"timestamp\":\\[(?<timestamp>.+?)]");
        Pattern lowPattern = Pattern.compile("\"low\":\\[(?<low>.+?)]");
        Pattern highPattern = Pattern.compile("\"high\":\\[(?<high>.+?)]");
        Pattern closePattern = Pattern.compile("\"close\":\\[(?<close>.+?)]");

        return Arrays.stream(coinNames)
                .parallel()
                .map(ticker -> {
                    try {
                        String url = "https://query2.finance.yahoo.com/v8/finance/chart/" + ticker + "-USD?period1=1509494400&period2=9999999999&interval=1d&events=history";
                        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                        int status = connection.getResponseCode();
                        if (status > 299) {
                            StringBuilder response = new StringBuilder(url);
                            response.append(System.lineSeparator()).append(status).append(" ").append(connection.getResponseMessage());
                            try (Scanner scanner = new Scanner(connection.getErrorStream()).useDelimiter("\\A")) {
                                if (scanner.hasNext()) {
                                    response.append(System.lineSeparator()).append(scanner.next());
                                }
                            }
                            connection.disconnect();
                            throw new IOException(response.toString());
                        }
                        try (Scanner scanner = new Scanner(connection.getInputStream()).useDelimiter("\\A")) {
                            if (!scanner.hasNext()) {
                                throw new IOException("Empty response from " + url);
                            }

                            String response = scanner.next();
                            String[] timestamps = extractList(timestampPattern, response);
                            String[] lows = extractList(lowPattern, response);
                            String[] highs = extractList(highPattern, response);
                            String[] closes = extractList(closePattern, response);

                            int nonNullsLength = Math.min(timestamps.length, Math.min(lows.length, Math.min(highs.length, closes.length)));
                            Day[] data = new Day[nonNullsLength];
                            for (int i = 0; i < nonNullsLength; i++) {
                                data[i] = new Day(timestamps[i], highs[i], lows[i], closes[i]);
                            }
                            return new Coin(ticker, data);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException("Ticker '" + ticker + '\'', e);
                    } catch (Throwable e) {
                        throw new RuntimeException("Ticker '" + ticker + '\'', e);
                    }
                });
    }

    private static String[] extractList(Pattern pattern, String response) throws IOException {
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            throw new IOException(pattern + " not in " + response);
        }
        String csvList = matcher.group(1);
        return Arrays.stream(csvList.split(",")).filter(v -> !"null".equals(v)).toArray(String[]::new);
    }

    private static class Coin {
        private final String name;
        private final Day[] data;

        public Coin(String name, Day[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private static class Day {
        private final String date;
        private final double high;
        private final double low;
        private final double close;

        public Day(String date, String high, String low, String close) {
            this.date = Instant.ofEpochSecond(Long.parseLong(date)).atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
            this.high = Double.parseDouble(high);
            this.low = Double.parseDouble(low);
            this.close = Double.parseDouble(close);
        }
    }

    private static class Result {
        private final double gain;
        private final String transactions;

        public Result(double gain, String transactions) {
            this.gain = gain;
            this.transactions = transactions;
        }
    }
}
