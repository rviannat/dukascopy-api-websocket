package com.ismail.dukascopy.controller;

import com.dukascopy.api.IBar;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.instrument.IFinancialInstrument.Type;
import com.ismail.dukascopy.model.ApiException;
import com.ismail.dukascopy.model.Candle;
import com.ismail.dukascopy.strategy.DukasStrategy;
import com.ismail.dukascopy.util.DukasUtil;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HistDataController {

    @Autowired
    private DukasStrategy strategy;

    @RequestMapping(value = "/api/v1/history", method = RequestMethod.GET)
    public List<Candle> getHistData(@RequestParam String instID,
            @RequestParam(defaultValue = "DAILY") String timeFrame, @RequestParam long from,
            @RequestParam(required = false, defaultValue = "0") String to) {

        Instrument instrument = Instrument.valueOf(instID);
        long timeTo = Long.parseLong(to);

        if (instrument == null)
            throw new ApiException("Invalid instrument: " + instID);

        Period period = null;

        switch (timeFrame) {
            case "1SEC":
                period = Period.ONE_SEC;
                break;
            case "10SEC":
                period = Period.TEN_SECS;
                break;
            case "1MIN":
                period = Period.ONE_MIN;
                break;
            case "5MIN":
                period = Period.FIVE_MINS;
                break;
            case "10MIN":
                period = Period.TEN_MINS;
                break;
            case "15MIN":
                period = Period.FIFTEEN_MINS;
                break;
            case "1HOUR":
                period = Period.ONE_HOUR;
                break;
            case "DAILY":
                period = Period.DAILY;
                break;
            default:
                period = Period.DAILY;
                break;
        }

        // from
        if (from == 0L) {
            from = System.currentTimeMillis() - DukasUtil.DAY * 5;
            // normalize time
            long periodInMillis = period.getInterval();

            from = from - from % periodInMillis;
        } else if (from > 0L) {
            Calendar calendar = Calendar.getInstance();
            int day = calendar.get(Calendar.DAY_OF_WEEK);
            if (day == Calendar.MONDAY) {
                from -= DukasUtil.DAY * 2;
            }
        }

        try {
            List<IBar> askBars =
                    strategy.getHistData(instrument, period, OfferSide.ASK, from, timeTo);
            List<IBar> bidBars =
                    strategy.getHistData(instrument, period, OfferSide.BID, from, timeTo);

            int pipsFactor = 1;

            for (int i = 0; i < instrument.getPipScale(); i++) {
                pipsFactor *= 10;
            }

            if (askBars != null && bidBars != null && bidBars.size() > 0) {
                ArrayList<Candle> list = new ArrayList<>(bidBars.size());

                for (int i = 0; i < bidBars.size(); i++) {
                    IBar askBar = askBars.get(i);
                    IBar bidBar = bidBars.get(i);
                    Candle st = new Candle();
                    st.open = askBar.getOpen();
                    st.high = askBar.getHigh();
                    st.low = askBar.getLow();
                    st.close = askBar.getClose();
                    st.volume = askBar.getVolume();
                    st.timestamp = askBar.getTime();
                    st.spread = (st.close - bidBar.getClose()) * pipsFactor;
                    if (instrument.getType() == Type.FOREX) {
                        st.volume *= 100000.0;
                    }

                    list.add(st);
                }

                return list;
            } else {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("getHistData() error: ", e.getMessage(), e);

            throw new ApiException("Server error: " + e.getMessage());
        }
    }
}
