package com.ismail.dukascopy.strategy.hft;

import com.dukascopy.api.*;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static com.dukascopy.api.IEngine.OrderCommand.BUY;
import static com.dukascopy.api.IEngine.OrderCommand.SELL;
import static java.math.RoundingMode.HALF_UP;

public class FastStrategy implements IStrategy {

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private IAccount account;
    private int counter = 0;
    private IOrder order;
    private IOrder pendingOrderBuy;
    private IOrder pendingOrderSell;
    @Configurable("Instrument")
    public Instrument instrument = Instrument.USDJPY;
    @Configurable("Period")
    public Period selectedPeriod = Period.ONE_SEC;
    @Configurable("Slippage")
    public double SLIPPAGE = 0;
    @Configurable("Amount")
    public double amount = 0.01;
    @Configurable("Place long first")
    public boolean nextLong = true;
    @Configurable("Take profit pips")
    public final int PROFIT_PIP = 0;
    @Configurable("Stop loss in pips")
    public final double LOSS_PIP = 1.5;
    private double stopLoss = .0;
    private Double ask = 0d, bid = 0d;
    private IContext context;
    private long TIME_OUT = 5000;
    private final int MAX_ORDER = 20;


    @Override
    public void onStart(IContext context) throws JFException {
        this.console = context.getConsole();
        this.indicators = context.getIndicators();
        this.history = context.getHistory();
        this.engine = context.getEngine();
        this.context = context;

    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {}

    public void onTick(Instrument inst, ITick tick) throws JFException {

        if (inst != this.instrument) { return; }

        try {
            if (getOpenPositions() < MAX_ORDER && inst.isTradable()) {

                stopLoss = tick.getBid() - getPipPrice();

                engine.submitOrder(getLabel(inst), inst, BUY, tick.getBidVolume(), tick.getBid(),
                           SLIPPAGE, getRoundedPrice(stopLoss), getRoundedPrice(tick.getAsk()), TIME_OUT);

                stopLoss = tick.getAsk() + getPipPrice();

                engine.submitOrder(getLabel(inst), inst, SELL, tick.getAskVolume(), tick.getAsk(),
                           SLIPPAGE, getRoundedPrice(stopLoss), getRoundedPrice(tick.getBid()), TIME_OUT);

            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private Integer getOpenPositions() throws Exception {
        if (context == null) throw new RuntimeException("Strategy context not initialized yet");
        return engine.getOrders().size();
    }

    private double getPipPrice() { return LOSS_PIP * this.instrument.getPipValue(); }

    private String getLabel(Instrument instrument) {
        String label = instrument.name();
        label = label + (counter++);
        return label.toUpperCase();
    }

    private double getRoundedPrice(double price) {
        BigDecimal bd = new BigDecimal(price);
        bd = bd.setScale(instrument.getPipScale() + 1, HALF_UP);
        return bd.doubleValue();
    }

    public void onMessage(IMessage message) throws JFException { }
    public void onAccount(IAccount account) throws JFException {this.account = account; }
    public void onStop() throws JFException { }

    public static String toStr(double[] arr) {
        String str = "";
        for (int r = 0; r < arr.length; r++) {
            str += "[" + r + "] " + (new DecimalFormat("#.#######")).format(arr[r]) + "; ";
        }
        return str;
    }

    public static String toStr(double[][] arr) {
        String str = "";
        if (arr == null) {
            return "null";
        }
        for (int r = 0; r < arr.length; r++) {
            for (int c = 0; c < arr[r].length; c++) {
                str += "[" + r + "][" + c + "] " + (new DecimalFormat("#.#######")).format(arr[r][c]);
            }
            str += "; ";
        }
        return str;
    }

    public String toStr(double d) {
        return (new DecimalFormat("#.#######")).format(d);
    }

    public String toStr(Long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") {
            {
                setTimeZone(TimeZone.getTimeZone("GMT"));
            }
        };
        return sdf.format(time);
    }
}
