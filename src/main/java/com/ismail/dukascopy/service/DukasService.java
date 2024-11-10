package com.ismail.dukascopy.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.ByteArrayInputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.dukascopy.api.Instrument;
import com.ismail.dukascopy.strategy.*;
import com.ismail.dukascopy.strategy.hft.Algo2Strategy;
import com.ismail.dukascopy.strategy.hft.FastStrategy;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.dukascopy.api.system.IClient;
import com.dukascopy.api.system.ISystemListener;
import com.ismail.dukascopy.config.DukasConfig;
import com.ismail.dukascopy.controller.JettyServer;
import com.ismail.dukascopy.controller.MarketDataWebsocket;

import lombok.extern.slf4j.Slf4j;

/**
 * Dukas Service 
 * 
 * @author ismail
 * @since 20220617
 */
@Service
@Slf4j
public class DukasService implements ISystemListener, InitializingBean, DisposableBean, ThreadFactory, UncaughtExceptionHandler, Runnable
{
    private static DukasService sMe = null;
    public final DukasConfig config;
    public final IClient client;
    public FastStrategy fastStrategy = new FastStrategy();
    public FastStrategy strategy2 = new FastStrategy();
    public Algo2Strategy strategy3 = new Algo2Strategy();
    public AlgosmartFirstStrategy4 strategy4 = new AlgosmartFirstStrategy4();
    public AlgosmartFirstStrategy strategy0 = new AlgosmartFirstStrategy();
    public AlgoBuy strategy5 = new AlgoBuy();
    public AlgoSell strategy6 = new AlgoSell();
    public Volume volume = new Volume();
    public final ThreadFactory delegate;
    public final ScheduledExecutorService executor;
    public JettyServer server = null;
    public static DukasService getInstance()
    {
        return sMe;
    }
    
    @Autowired
    public DukasService(DukasConfig config, IClient client, DukasStrategy strategy)
    {
        sMe = this;
        
        this.config = Objects.requireNonNull(config, "Config is required.");

        this.client = Objects.requireNonNull(client, "IClient is required.");



        this.delegate = Executors.defaultThreadFactory();

        this.executor = Executors.newSingleThreadScheduledExecutor(this);

        try
        {
            startJettyServer();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void startJettyServer() throws Exception
    {
        log.info("startJettyServer()");

        server = new JettyServer();
        server.addWebsocket("/ticker/*", MarketDataWebsocket.class);

        // TODO add servlets for RestAPI

        server.configureWebsockets();

        int portNumber = config.getWsServerPort();

        server.setPort(portNumber);

        server.start();
    }

    @Override
    public Thread newThread(Runnable r)
    {

        Thread thread = delegate.newThread(r);

        thread.setDaemon(true);

        thread.setName(getClass().getSimpleName());

        thread.setUncaughtExceptionHandler(this);

        return thread;

    }

    @Override
    public void uncaughtException(Thread t, Throwable e)
    {

        log.error("Uncaught exception : {}", t, e);

    }

    @Override
    public void afterPropertiesSet()
    {

        log.info("Initializing application.");

        client.setSystemListener(this);

        executor.execute(this);

    }

    @Override
    public void destroy() throws InterruptedException
    {

        long millis = config.getLifecycleWait();

        log.info("Terminating application : await = {} ms", millis);

        executor.shutdownNow();

        executor.awaitTermination(millis, MILLISECONDS);

        client.disconnect();

        log.info("Terminated application. (graceful = {})", executor.isTerminated());

    }

    @Override
    public synchronized void onConnect()
    {

        log.info("IClient connected.");

        if (executor.isShutdown())
        {
            return;
        }

        client.startStrategy(volume);
        log.info("Started strategy : [{}] {}", 1, "Designed to verify bid and ask volume");
        client.startStrategy(fastStrategy);
        log.info("Started strategy : [{}] {}", 2, "create bids and asks simultaneously with a small price looking for a fast oscillation");
        client.startStrategy(strategy2);
        log.info("Started strategy : [{}] {}", 3, "create multiples order but with a bigger range");
        //id = client.startStrategy(strategy3);
        //log.info("Started strategy : [{}] {}", 4, strategy3);
        //id = client.startStrategy(strategy4);
        //log.info("Started strategy : [{}] {}", 5, strategy4);
        //id = client.startStrategy(strategy4);
        //log.info("Started strategy : [{}] {}", 6, strategy5);
        //id = client.startStrategy(strategy6);
        //log.info("Started strategy : [{}] {}", 7, strategy6);
        //id = client.startStrategy(strategy4);
        //log.info("Started strategy : [{}] {}", 8, strategy4);

    }

    @Override
    public synchronized void onDisconnect()
    {

        log.info("IClient disconnected.");

        Set<Instrument> instruments = new HashSet<Instrument>();
        instruments.add(Instrument.EURUSD);
        instruments.add(Instrument.GBPUSD);
        instruments.add(Instrument.USDCHF);
        instruments.add(Instrument.USDJPY);
        client.setSubscribedInstruments(instruments);

        //client.startStrategy(strategy0);
        //client.startStrategy(strategy1);
        //client.startStrategy(strategy2);
        client.startStrategy(strategy2);
        //client.startStrategy(strategy4);
        //client.startStrategy(strategy5);
        client.startStrategy(fastStrategy);
        //client.startStrategy(volume);





        client.getStartedStrategies().forEach((id, strategy) -> {

            client.stopStrategy(id);

            log.info("Stopped strategy : [{}] {}", id, strategy);

        });

        if (executor.isShutdown())
        {
            return;
        }

        executor.execute(this); // Attempt reconnect.

    }

    @Override
    public void run()
    {

        String jnlp = config.getCredentialJnlp();
        String user = config.getCredentialUsername();
        String pass = config.getCredentialPassword();

        try
        {

            String hash = DigestUtils.md5DigestAsHex(new ByteArrayInputStream(pass.getBytes(UTF_8)));

            log.info("IClient connecting... (url={}, user={}, pass=MD5:{})", jnlp, user, hash);

            client.connect(jnlp, user, pass);

        }
        catch (Exception e)
        {
            if (executor.isShutdown())
            {
                return;
            }

            long millis = config.getConnectionWait();
            if (millis <= 0L)
                millis = 1000L;
            
            log.warn("Dukas IClient connection failure: "+ e.getMessage(), ". Reconnecting in {} ms...", millis, e);

            executor.schedule(this, millis, MILLISECONDS);

        }

    }

    @Override
    public void onStart(long processId)
    {
        log.info("IClient process started : {}", processId);
    }

    @Override
    public void onStop(long processId)
    {
        log.info("IClient process stopped : {}", processId);
    }

}
