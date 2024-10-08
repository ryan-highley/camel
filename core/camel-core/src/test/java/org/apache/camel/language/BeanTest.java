/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.language;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Header;
import org.apache.camel.LanguageTestSupport;
import org.apache.camel.Message;
import org.apache.camel.NoSuchBeanException;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.apache.camel.language.bean.BeanExpression;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BeanTest extends LanguageTestSupport {

    @Test
    public void testSimpleExpressions() {
        assertExpression("foo.echo('e::o')", "e::o");
        assertExpression("foo.echo('e.o')", "e.o");
        assertExpression("my.company.MyClass::echo('a')", "a");
        assertExpression("my.company.MyClass::echo('a.b')", "a.b");
        assertExpression("my.company.MyClass::echo('a::b')", "a::b");
        assertExpression("foo.cheese", "abc");
        assertExpression("foo?method=cheese", "abc");
        assertExpression("my.company.MyClass::cheese", "abc");
        assertExpression("foo?method=echo('e::o')", "e::o");
    }

    @Test
    public void testPredicates() {
        assertPredicate("foo.isFooHeaderAbc");
        assertPredicate("foo?method=isFooHeaderAbc");
        assertPredicate("my.company.MyClass::isFooHeaderAbc");
    }

    @Test
    public void testDoubleColon() {
        assertPredicate("foo::isFooHeaderAbc");
        NoSuchBeanException e = assertThrows(NoSuchBeanException.class,
                () -> assertPredicateFails("foo:isFooHeaderAbc"),
                "Should throw exception");

        assertEquals("foo:isFooHeaderAbc", e.getName());
    }

    @Test
    public void testBeanTypeExpression() {
        Expression exp = new BeanExpression(MyUser.class, null);
        exp.init(context);
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertEquals("Hello Claus", result);
    }

    @Test
    public void testBeanTypeAndMethodExpression() {
        Expression exp = new BeanExpression(MyUser.class, "hello");
        exp.init(context);
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertEquals("Hello Claus", result);
    }

    @Test
    public void testBeanInstanceAndMethodExpression() {
        MyUser user = new MyUser();
        Expression exp = new BeanExpression(user, "hello");
        exp.init(context);
        Exchange exchange = createExchangeWithBody("Claus");

        Object result = exp.evaluate(exchange, Object.class);
        assertEquals("Hello Claus", result);
    }

    @Test
    public void testNoMethod() {
        MyUser user = new MyUser();
        Exception e = assertThrows(Exception.class, () -> {
            Expression exp = new BeanExpression(user, "unknown");
            exp.init(context);
        }, "Should throw exception");

        MethodNotFoundException mnfe = assertIsInstanceOf(MethodNotFoundException.class, e);
        assertSame(user, mnfe.getBean());
        assertEquals("unknown", mnfe.getMethodName());
    }

    @Test
    public void testNoMethodBeanLookup() {
        MethodNotFoundException e = assertThrows(MethodNotFoundException.class, () -> {
            Expression exp = new BeanExpression("foo", "cake");
            exp.init(context);
        }, "Should throw exception");

        assertEquals("cake", e.getMethodName());
    }

    @Override
    protected String getLanguageName() {
        return "bean";
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry answer = super.createCamelRegistry();
        answer.bind("foo", new MyBean());
        answer.bind("my.company.MyClass", new MyBean());
        return answer;
    }

    public static class MyBean {
        public Object cheese(Exchange exchange) {
            Message in = exchange.getIn();
            return in.getHeader("foo");
        }

        public String echo(String echo) {
            return echo;
        }

        public boolean isFooHeaderAbc(@Header("foo") String foo) {
            return "abc".equals(foo);
        }
    }

    public static class MyUser {
        public String hello(String name) {
            return "Hello " + name;
        }
    }
}
