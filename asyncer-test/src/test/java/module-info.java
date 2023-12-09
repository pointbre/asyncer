module com.github.pointbre.asyncer.test {
    requires com.github.pointbre.asyncer.core;
    requires reactor.core;
    requires transitive org.reactivestreams;
    requires static lombok;
    requires transitive org.junit.jupiter.api;
    requires transitive org.junit.jupiter.engine;
    requires transitive org.junit.jupiter.params;

    // JUnit requires to access our test classes via reflection
    opens com.github.pointbre.asyncer.test to org.junit.platform.commons;
    // requires org.slf4j;
    // requires org.slf4j.simple;
}