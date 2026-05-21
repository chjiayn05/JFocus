package jfocus.ai.distraction;

import jfocus.monitor.WindowSession;

@FunctionalInterface
public interface DistractionUserNotifier {
    void notifyDontBeDistracted(WindowSession session);
}
