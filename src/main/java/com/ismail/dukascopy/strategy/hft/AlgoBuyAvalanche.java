package com.ismail.dukascopy.strategy.hft;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.indicators.IIndicator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

@Service
public class AlgoBuyAvalanche implements IStrategy {

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
    public Instrument instrument = Instrument.EURUSD;
    @Configurable("Period")
    public Period selectedPeriod = Period.ONE_SEC;
    @Configurable("Slippage")
    public double slippage = 0;
    @Configurable("Amount")
    public double amount = 0.01;
    @Configurable("Place long first")
    public boolean nextLong = true;
    @Configurable("Take profit pips")
    public final int PROFIT_PIP = 1;
    @Configurable("Stop loss in pips")
    public final int LOSS_PIP = 1;
    private Double stopLossPrice = 2d, takeProfitPrice = .1;
    private Double ask = 0d, bid = 0d;
    private IContext context;
    private final long TIME_OUT = 5000;
    private final int MAX_ORDER = 30;


    @Override
    public void onStart(IContext context) throws JFException {
        this.console = context.getConsole();
        this.indicators = context.getIndicators();
        this.history = context.getHistory();
        this.engine = context.getEngine();
        this.context = context;

    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {

    }

    public List<IOrder> getPositions() throws Exception {
        if (context == null)
            throw new RuntimeException("Strategy context not initialized yet");
        return engine.getOrders();
    }

    public void onTick(Instrument inst, ITick tick) throws JFException {
        try {
            if (getPositions().size() > MAX_ORDER && inst.isTradable()) {
                if(tick.getBidVolume() > 1) {
                    stopLossPrice = tick.getBid() - getPipPrice(LOSS_PIP);
                    for(double avalancheVolume = .01; avalancheVolume < tick.getBidVolume(); avalancheVolume += .01) {
                        engine.submitOrder(getLabel(inst), inst, OrderCommand.BUY, avalancheVolume, tick.getBid(),
                                slippage, stopLossPrice,takeProfitPrice,TIME_OUT);
                    }
                }
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void closeOrder(IOrder order) throws JFException {
        if (isActive(order)) {
            order.close();
        }
    }

    private boolean isActive(IOrder order) throws JFException {
        return order != null && order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CREATED && order.getState() != IOrder.State.CANCELED;
    }

    private double getPipPrice(double pips) {
        return pips * this.instrument.getPipValue();
    }

    private String getLabel(Instrument instrument) {
        String label = instrument.name();
        label = label + (counter++);
        label = label.toUpperCase();
        return label;
    }

    private double getRoundedPrice(double price) {
        BigDecimal bd = new BigDecimal(price);
        bd = bd.setScale(instrument.getPipScale() + 1, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    private double getRoundedPips(double pips) {
        BigDecimal bd = new BigDecimal(pips);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public void onMessage(IMessage message) throws JFException {
    }

    public void onAccount(IAccount account) throws JFException {
        this.account = account;
    }

    public void onStop() throws JFException {
    }


    private void print(Object o) {
        console.getOut().println(o);
    }

    private void print2(Object o) {
        console.getOut().print(o);
    }

    private void print(double d) {
        print(toStr(d));
    }

    private void print(double[] arr) {
        print(toStr(arr));
    }

    private void print(double[][] arr) {
        print(toStr(arr));
    }

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

    private String toStr(IBar bar) {
        return toStr(bar.getTime()) + "  O:" + bar.getOpen() + " C:" + bar.getClose() + " H:" + bar.getHigh() + " L:" + bar.getLow();
    }

    private void printTime(Long time) {
        console.getOut().println(toStr(time));
    }
}
