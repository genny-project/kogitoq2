package life.genny.qwandaq.exception;

import java.lang.invoke.MethodHandles;

import org.jboss.logging.Logger;

/**
 * Custom Genny System exception to identify 
 * common issues within the logic.
 *
 * @author Bryn Meacham
 * @author Jasper Robison
 */
public class GennyException extends Exception {

	static final Logger log = Logger.getLogger(MethodHandles.lookup().lookupClass().getCanonicalName());
    
    public GennyException() {
        super();
    }

	public GennyException(String message) {
		super(message);
	}

	public GennyException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    @Override
    public void printStackTrace() {
        GennyExceptionBase.printStackTrace(this, false);
    }
}