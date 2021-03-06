package roboguice.util;


import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(RobolectricTestRunner.class)
public class AndroidCallableTest {

    @Test
    public void shouldCallMethodsUsingProperThreads() throws Exception {
        final Thread fgThread = Thread.currentThread();
        final Thread[] bgThread = {null};
        final Thread[] answers = new Thread[5];
        final ShadowLooper looper = Robolectric.shadowOf(Looper.getMainLooper());

        Executors.newSingleThreadExecutor(new MyThreadFactory(bgThread)).submit(new StringAndroidCallable(answers,false));

        // Run all the pending tasks on the ui thread
        while(answers[answers.length-1]==null)
            looper.runToEndOfTasks();


        final Thread[] correctAnswer = new Thread[]{fgThread, bgThread[0], null, fgThread, fgThread };
        assertThat( answers, equalTo(correctAnswer));
    }

    @Test
    public void shouldCallMethodsUsingProperThreadsWithException() throws Exception {
        final Thread fgThread = Thread.currentThread();
        final Thread[] bgThread = {null};
        final Thread[] answers = new Thread[5];
        final ShadowLooper looper = Robolectric.shadowOf(Looper.getMainLooper());

        Executors.newSingleThreadExecutor(new MyThreadFactory(bgThread)).submit(new StringAndroidCallable(answers,true));

        // Run all the pending tasks on the ui thread
        while(answers[answers.length-1]==null)
            looper.runToEndOfTasks();


        final Thread[] correctAnswer = new Thread[]{fgThread, bgThread[0], fgThread, null, fgThread };
        assertThat( answers, equalTo(correctAnswer));
    }

    @Test
    public void shouldHaveCorrectStackTrace() {
        final Exception[] exception = {null};
        final StackTraceElement[] here;
        final StackTraceElement[][] there = new StackTraceElement[][]{null};
        final ShadowLooper looper = Robolectric.shadowOf(Looper.getMainLooper());

        try {
            throw new UnsupportedOperationException();
        } catch (UnsupportedOperationException e) {
            here = e.getStackTrace();
        }

        Executors.newSingleThreadExecutor(new MyThreadFactory(new Thread[]{null})).submit(new AndroidCallable<String>() {
            @Override
            public String doInBackground() throws Exception {
                try {
                    throw new NullPointerException();
                } catch (NullPointerException e) {
                    there[0] = e.getStackTrace();
                    throw e;
                }
            }

            @Override
            public void onException(Exception e) {
                exception[0] = e;
            }

            @Override
            public void onSuccess(String result) {
            }

        },false);

        // Run all the pending tasks on the ui thread
        while(exception[0]==null)
            looper.runToEndOfTasks();

        assertThat( exception[0].getStackTrace().length, equalTo(here.length + there[0].length + 3)  );

        int i=0;
        while (i<there[0].length) {
            assertThat( exception[0].getStackTrace()[i], equalTo(there[0][i]));
            ++i;
        }

        for( int j=i+3, k=0; k<here.length; ++k, ++j) { // skip 3 frames due to differences in where we got our stacktrace from
            assertThat( exception[0].getStackTrace()[j].getFileName(), equalTo(here[k].getFileName())); // line numbers may be off
        }
    }

    private static class StringAndroidCallable extends AndroidCallable<String> {
        Thread[] answers;
        boolean shouldThrowException;

        public StringAndroidCallable(Thread[] answers, boolean shouldThrowException ) {
            this.answers = answers;
            this.shouldThrowException = shouldThrowException;
        }

        @Override
        public void onPreCall() {
            answers[0] = Thread.currentThread();
        }

        @Override
        public String doInBackground() throws Exception {
            answers[1] = Thread.currentThread();

            if( shouldThrowException )
                throw new UnsupportedOperationException();

            return "12345";
        }

        @Override
        public void onException(Exception e) {
            answers[2] = Thread.currentThread();
        }

        @Override
        public void onSuccess(String result) {
            answers[3] = Thread.currentThread();
        }

        @Override
        public void onFinally() {
            answers[4] = Thread.currentThread();
        }


    }

    private static class MyThreadFactory implements ThreadFactory {
        private final Thread[] bgThread;

        public MyThreadFactory(Thread[] bgThread) {
            this.bgThread = bgThread;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            bgThread[0] = new Thread(runnable);
            bgThread[0].setName("bgThread");
            return bgThread[0];
        }
    }
}
