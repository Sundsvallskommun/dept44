package se.sundsvall.dept44.configuration.feign.retryer;

@FunctionalInterface
public interface Action {
    void execute();
}
