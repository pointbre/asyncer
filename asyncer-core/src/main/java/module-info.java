module com.github.pointbre.asyncer.core {

    requires reactor.core;
    requires transitive org.reactivestreams;

    requires static lombok;

    exports com.github.pointbre.asyncer.core;
}