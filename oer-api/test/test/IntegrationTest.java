package test;
/* Copyright 2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

import org.junit.*;

import play.mvc.*;
import play.test.*;
import play.libs.F.*;

import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;

import static org.fluentlenium.core.filter.FilterConstructor.*;

public class IntegrationTest {

    /**
     * add your integration test here
     * in this example we just check if the welcome page is being shown
     */
    @Test
    @Ignore
    public void test() {
        running(testServer(2222, fakeApplication(inMemoryDatabase())), HTMLUNIT, new Callback<TestBrowser>() {
            public void invoke(TestBrowser browser) {
                browser.goTo("http://localhost:2222/oer");
                assertThat(browser.pageSource()).contains("OER API");
            }
        });
    }

}