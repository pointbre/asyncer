module com.github.pointbre.asyncer.core {

    requires reactor.core;
    requires org.reactivestreams;
    requires static lombok;
    requires dev.failsafe.core;

    exports com.github.pointbre.asyncer.core;
}