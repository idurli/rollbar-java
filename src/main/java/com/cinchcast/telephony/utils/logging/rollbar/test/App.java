package com.cinchcast.telephony.utils.logging.rollbar.test;

import org.apache.log4j.Logger;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        Logger logger = Logger.getLogger(App.class);
        logger.debug("this is a sample debug log message.");

        // init Rollbar notifier
        /*String url = "https://api.rollbar.com/api/1/item/";
        String apiKey = "b550123d5bea44f9987e629e3fc8493c";
        String environment = "dev";
        RollbarNotifier.init(url, apiKey, environment);

        Map context = new HashMap();
        context.put("test", "test");
*/
        try {
            throw new Exception("request: Telephony-BridgeAppSrvr - Test rollbar exception");
        } catch (Throwable throwable) {
            logger.error(throwable);
        }
    }
}
