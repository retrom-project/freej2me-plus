package org.recompile.mobile;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;

/** Exercises application callbacks through the actual Display event thread. */
public final class MiniJvmDisplayTransitionTest {
    public static void main(String[] args) {
        try {
            System.setProperty("file.encoding", Mobile.textEncoding);
            Mobile.setPlatform(new MobilePlatform(240, 320), new Runnable() { public void run() { } });
            final Display display = new Display();
            Mobile.setDisplay(display);
            final AtomicInteger shown = new AtomicInteger();
            final CountDownLatch painted = new CountDownLatch(1);
            Canvas self = new Canvas() {
                public void showNotify() {
                    if (shown.incrementAndGet() > 4) throw new AssertionError("recursive showNotify");
                    display.setCurrent(this);
                }
                protected void paint(Graphics graphics) { painted.countDown(); }
            };
            display.setCurrent(self);
            check(painted.await(2, TimeUnit.SECONDS), "self selection never painted");
            check(shown.get() == 1, "self selection repeated showNotify");
            final CountDownLatch finalPaint = new CountDownLatch(1);
            final AtomicInteger nested = new AtomicInteger();
            final Canvas destination = new Canvas() {
                public void showNotify() { nested.incrementAndGet(); }
                protected void paint(Graphics graphics) { finalPaint.countDown(); }
            };
            Canvas redirect = new Canvas() {
                public void showNotify() { display.setCurrent(destination); }
                protected void paint(Graphics graphics) { }
            };
            display.setCurrent(redirect);
            check(finalPaint.await(2, TimeUnit.SECONDS), "nested destination never painted");
            check(display.getCurrent() == destination && nested.get() == 1, "nested transition lost");
            final CountDownLatch hideRedirected = new CountDownLatch(1);
            final Canvas afterHide = new Canvas() {
                protected void paint(Graphics graphics) { hideRedirected.countDown(); }
            };
            Canvas redirectOnHide = new Canvas() {
                public void hideNotify() { display.setCurrent(afterHide); }
                protected void paint(Graphics graphics) { }
            };
            display.setCurrent(redirectOnHide);
            awaitCurrent(display, redirectOnHide);
            display.setCurrent(destination);
            check(hideRedirected.await(2, TimeUnit.SECONDS), "hide callback transition lost");
            check(display.getCurrent() == afterHide, "hide callback destination lost");

            final AtomicInteger supersededShows = new AtomicInteger();
            final Canvas superseded = new Canvas() {
                public void showNotify() { supersededShows.incrementAndGet(); }
                protected void paint(Graphics graphics) { }
            };
            final CountDownLatch overwritten = new CountDownLatch(1);
            display.callSerially(new Runnable() { public void run() {
                display.setCurrent(superseded);
                display.setCurrent(destination);
                overwritten.countDown();
            }});
            check(overwritten.await(2, TimeUnit.SECONDS), "event callback did not execute");
            awaitCurrent(display, destination);
            check(supersededShows.get() == 0, "superseded request was executed");

            Alert dismissed = new Alert("Dismiss", "Test", null, null);
            display.setCurrent(dismissed, destination);
            awaitCurrent(display, dismissed);
            dismissed.defaultListener.commandAction(Alert.DISMISS_COMMAND, dismissed);
            awaitCurrent(display, destination);
            Alert timed = new Alert("Timed", "Test", null, null);
            display.setCurrent(timed, destination);
            awaitCurrent(display, timed);
            timed.setTimeout(100);
            awaitCurrent(display, destination);
            System.out.println("Display self-selection, nested/hide callbacks, coalescing and Alert dismissal/timeout verified.");
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }
    private static void awaitCurrent(Display display, javax.microedition.lcdui.Displayable expected) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        while (display.getCurrent() != expected && System.currentTimeMillis() < deadline) Thread.sleep(5);
        check(display.getCurrent() == expected, "display transition deadline exceeded");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
