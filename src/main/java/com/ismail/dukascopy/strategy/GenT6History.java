package com.ismail.dukascopy.strategy;

import com.dukascopy.api.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.*;

public class GenT6History implements IStrategy {
    private static final int BATCH_SIZE = 10000;
    private static final int DAY_MILLIS = 24 * 3600 * 1000;
    private static final String TARGET_DIR = "/backtest";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(
            "yyyy-MM-dd HH:mm"
    );
    private static final boolean WRITE_CSV = false;

    private static final OutputStream NULL = new OutputStream() {

        @Override
        public void write(int b) throws IOException {
            // no-op
        }
    };

    private static final Map<Instrument, String> INSTRUMENT_FILE_MAP;

    static {
        INSTRUMENT_FILE_MAP = new HashMap<>();
        INSTRUMENT_FILE_MAP.put(Instrument.EURUSD, "EURUSD");

    }

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IContext context;
    private IIndicators indicators;
    private IUserInterface userInterface;

    private Date parseDate(String stringDate) {
        try {
            return DATE_FORMAT.parse(stringDate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private File getInstrumemntFile(Instrument instrument, int year, String extension) {
        String instrumentFile = INSTRUMENT_FILE_MAP.getOrDefault(
                instrument,
                instrument.getName().replaceAll("/", "")
        );

        return new File(
                String.format("%s/%s_%d.%s", TARGET_DIR, instrumentFile, year, extension)
        );
    }

    public void onStart(IContext context) throws JFException {
        this.engine = context.getEngine();
        this.console = context.getConsole();
        this.history = context.getHistory();
        this.context = context;
        this.indicators = context.getIndicators();
        this.userInterface = context.getUserInterface();

        Date startDate = parseDate("2000-07-11 00:00");
        // Date startDate = parseDate("2020-01-01 00:00");
        // Optional<Date> maybeEndDate = Optional.of(parseDate("2020-12-31 23:59"));
        Optional<Date> maybeEndDate = Optional.empty();

        for (Instrument instrument : Arrays.asList(

                Instrument.EURUSD,
                Instrument.USDCHF

        )) {
            saveInstrumentHistory(instrument, startDate, maybeEndDate);
        }

        console.getOut().println("Done");
    }

    public void onAccount(IAccount account) throws JFException {}

    public void onMessage(IMessage message) throws JFException {}

    public void onStop() throws JFException {}

    public void onTick(Instrument instrument, ITick tick) throws JFException {}

    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar)
            throws JFException {}

    private double toOLETimestamp(long timestamp) {
        return ((double) timestamp) / DAY_MILLIS + 25569;
    }

    private int yearOf(Date date) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTime(date);
        return calendar.get(Calendar.YEAR);
    }

    private int yearOf(long timestamp) {
        return yearOf(new Date(timestamp));
    }

    private void saveInstrumentHistory(
            Instrument instrument,
            Date startDate,
            Optional<Date> maybeEndDate
    ) throws JFException {
        Date endDate = maybeEndDate.orElse(
                new Date(history.getLastTick(instrument).getTime())
        );

        Period period = Period.ONE_MIN;
        long currentBarTime = history.getBarStart(period, endDate.getTime());
        int currentYear = yearOf(currentBarTime);

        while (currentBarTime >= startDate.getTime()) {
            File binaryFile = getInstrumemntFile(instrument, currentYear, "t6");
            console.getOut().println(String.format("Writing binary file %s", binaryFile));

            try (
                    Writer writer = WRITE_CSV
                            ? new FileWriter(getInstrumemntFile(instrument, currentYear, "csv"))
                            : new OutputStreamWriter(NULL);
                    FileOutputStream outputStream = new FileOutputStream(binaryFile)
            ) {
                while (currentBarTime >= startDate.getTime()) {
                    List<IBar> bars = history.getBars(
                            instrument,
                            period,
                            OfferSide.BID,
                            Filter.NO_FILTER,
                            BATCH_SIZE,
                            currentBarTime,
                            0
                    );
                    Collections.reverse(bars);
                    ByteBuffer buffer = ByteBuffer.allocate(BATCH_SIZE * 32);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);

                    for (IBar bar : bars) {
                        currentBarTime = bar.getTime();
                        if (
                                currentBarTime >= startDate.getTime() &&
                                        yearOf(currentBarTime) == currentYear
                        ) {
                            // see https://zorro-project.com/manual/en/history.htm
                            buffer.putDouble(toOLETimestamp(currentBarTime));
                            buffer.putFloat((float) bar.getHigh());
                            buffer.putFloat((float) bar.getLow());
                            buffer.putFloat((float) bar.getOpen());
                            buffer.putFloat((float) bar.getClose());
                            buffer.putFloat(0.0f); // fVol
                            buffer.putFloat((float) bar.getVolume());

                            if (WRITE_CSV) {
                                writer.append(
                                        DATE_FORMAT.format(new Date(bar.getTime())) +
                                                "," +
                                                bar.getTime() +
                                                "," +
                                                bar.getOpen() +
                                                "," +
                                                bar.getHigh() +
                                                "," +
                                                bar.getLow() +
                                                "," +
                                                bar.getClose() +
                                                "\n"
                                );
                            }
                        } else {
                            break;
                        }
                    }

                    buffer.flip();
                    outputStream.getChannel().write(buffer);

                    if (yearOf(currentBarTime) != currentYear) {
                        currentYear = yearOf(currentBarTime);
                        break; // break into outer while loop to recreate files
                    }
                }
            } catch (IOException e) {
                console.getOut().println(String.format("Error writing file %s", e.getMessage()));
                throw new RuntimeException(e);
            }
        }
    }
}
