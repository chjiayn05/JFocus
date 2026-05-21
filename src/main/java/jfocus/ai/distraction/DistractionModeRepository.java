package jfocus.ai.distraction;

public interface DistractionModeRepository {
    DistractionHandlingMode loadMode(DistractionHandlingMode defaultMode);

    void saveMode(DistractionHandlingMode mode);
}
