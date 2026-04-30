package jfocus.ai.distraction;

import jfocus.monitor.WindowSession;

public interface DistractingTargetCloser {
    boolean closeDistractingTarget(WindowSession session, boolean closeTabOnly);
}
