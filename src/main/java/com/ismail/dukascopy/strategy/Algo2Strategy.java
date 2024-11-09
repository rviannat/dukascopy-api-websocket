package com.ismail.dukascopy.strategy;

import com.dukascopy.api.*;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.indicators.IIndicator;
import com.ismail.dukascopy.config.DukasConfig;
import com.ismail.dukascopy.model.DukasSubscription;
import com.ismail.dukascopy.util.DukasUtil;
import org.apache.commons.collections.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class Algo2Strategy implements IStrategy {

    private IEngine engine;
    private IConsole console;
    private IHistory history;
    private IIndicators indicators;
    private IAccount account;
    private int counter = 0;
    private IOrder order;
    private IOrder pendingOrder;
    @Configurable("Instrument")
    public Instrument instrument = Instrument.GBPUSD;
    @Configurable("Period")
    public Period selectedPeriod = Period.ONE_SEC;
    @Configurable("Slippage")
    public double slippage = 0;
    @Configurable("Amount")
    public double amount = 0.01;
    @Configurable("Place long first")
    public boolean nextLong = true;
    @Configurable("Take profit pips")
    public int takeProfitPips = 3;
    @Configurable("Stop loss in pips")
    public int stopLossPips = 20;
    private IContext context;
    private final AtomicReference<IContext> reference = new AtomicReference<>();
    private DukasConfig config;
    private DukasSubscription dukasSubscription = null;
    public double dynamicAmount = amount;

    @Override
    public void onStart(IContext context) throws JFException {
        this.console = context.getConsole();
        this.indicators = context.getIndicators();
        this.history = context.getHistory();
        this.engine = context.getEngine();
        reference.set(context);
        this.context = context;



    }

    @Override
    public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
        if (period != this.selectedPeriod || instrument != this.instrument) {
            return;
        }
    }

    public void onTick(Instrument instrument, ITick tick) throws JFException {

        if (instrument != this.instrument) {
            return;
        }

        try {
            if (getPositions().size() < 50) {

                if (tick.getBid() > tick.getAsk()) {
                    submitOrder(OrderCommand.BUYLIMIT, tick, instrument);
                    submitOrder(OrderCommand.SELLLIMIT, tick, instrument);
                }
            }
        } catch (Exception e) {
            throw new JFException(e);
        }




    }

    private IOrder submitOrder(OrderCommand orderCmd, ITick tick, Instrument instr) throws JFException {

        double stopLossPrice = 0.0, takeProfitPrice = 0.0;

        // Calculating order price, stop loss and take profit prices
        if (orderCmd.isLong()) {

            if (stopLossPips > 0) {
                stopLossPrice = tick.getAsk() - getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = tick.getBid() + getPipPrice(takeProfitPips);
            }

        } else {

            if (stopLossPips > 0) {
                stopLossPrice = tick.getBid() + getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = tick.getAsk() - getPipPrice(takeProfitPips);
            }
        }

        return engine.submitOrder(getLabel(instr), instr, orderCmd, dynamicAmount, 0, slippage, getRoundedPrice(stopLossPrice), getRoundedPrice(takeProfitPrice));
    }

    private IOrder submitOrder(OrderCommand orderCmd, Instrument instr, double price) throws JFException {

        double stopLossPrice = 0.0, takeProfitPrice = 0.0;

        // Calculating order price, stop loss and take profit prices
        if (orderCmd.isLong()) {
            if (stopLossPips > 0) {
                stopLossPrice = price - getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = price + getPipPrice(takeProfitPips);
            }

        } else {
            if (stopLossPips > 0) {
                stopLossPrice = price + getPipPrice(stopLossPips);
            }
            if (takeProfitPips > 0) {
                takeProfitPrice = price - getPipPrice(takeProfitPips);
            }
        }

        return engine.submitOrder(getLabel(instr), instr, orderCmd, dynamicAmount, price, slippage, getRoundedPrice(stopLossPrice), getRoundedPrice(takeProfitPrice));

    }

    private void closeOrder(IOrder order) throws JFException {
        if (order != null && isActive(order)) {
            order.close();
        }
    }

    private boolean isActive(IOrder order) throws JFException {
        if (order != null && order.getState() != IOrder.State.CLOSED && order.getState() != IOrder.State.CREATED && order.getState() != IOrder.State.CANCELED) {
            return true;
        }
        return false;
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
        IContext context = reference.getAndSet(null);

        if (context == null) {
            return;
        }
    }

    public List<IOrder> getPositions() throws Exception {
        if (context == null)
            throw new RuntimeException("Strategy context not initialized yet");
        return engine.getOrders();
    }

    /**************** debug print functions ***********************/
    private void print(Object... o) {
        for (Object ob : o) {
            //console.getOut().print(ob + "  ");
            if (ob instanceof Double) {
                print2(toStr((Double) ob));
            } else if (ob instanceof double[]) {
                print((double[]) ob);
            } else if (ob instanceof double[]) {
                print((double[][]) ob);
            } else if (ob instanceof Long) {
                print2(toStr((Long) ob));
            } else if (ob instanceof IBar) {
                print2(toStr((IBar) ob));
            } else {
                print2(ob);
            }
            print2(" ");
        }
        console.getOut().println();
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

    private void print(IBar bar) {
        print(toStr(bar));
    }

    private void printIndicatorInfos(IIndicator ind) {
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfInputs(); i++) {
            print(ind.getIndicatorInfo().getName() + " Input " + ind.getInputParameterInfo(i).getName() + " " + ind.getInputParameterInfo(i).getType());
        }
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOptionalInputs(); i++) {
            print(ind.getIndicatorInfo().getName() + " Opt Input " + ind.getOptInputParameterInfo(i).getName() + " " + ind.getOptInputParameterInfo(i).getType());
        }
        for (int i = 0; i < ind.getIndicatorInfo().getNumberOfOutputs(); i++) {
            print(ind.getIndicatorInfo().getName() + " Output " + ind.getOutputParameterInfo(i).getName() + " " + ind.getOutputParameterInfo(i).getType());
        }
        console.getOut().println();
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

    public DukasSubscription adjustSubscription(String id, Set<Instrument> instruments) {
        DukasSubscription subscription = new DukasSubscription();
        subscription.id = id;
        subscription.time = System.currentTimeMillis();
        subscription.instruments = instruments;

        IContext context = reference.get();

        if (context != null) {
            Set<Instrument> current = context.getSubscribedInstruments();

            Set<Instrument> excessive = new TreeSet<>();
            Set<Instrument> lacking = new TreeSet<>();

            for (Instrument inst : current) {
                if (instruments.contains(inst) == false)
                    excessive.add(inst);
            }

            for (Instrument inst : instruments) {
                if (current.contains(inst) == false)
                    lacking.add(inst);
            }

            if (CollectionUtils.isNotEmpty(excessive)) {


                context.unsubscribeInstruments(new HashSet<>(excessive));
            }

            if (CollectionUtils.isNotEmpty(lacking)) {


                context.setSubscribedInstruments(new HashSet<>(lacking), false);
            }

            subscription.success = true;


        } else {
            subscription.success = false;


        }

        return subscription;
    }

}
