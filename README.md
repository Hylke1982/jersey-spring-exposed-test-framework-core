Spring exposed jersey test framework
====================================

This framework has the goal to do unit/integration testing with JUnit, Jersey (2.8+) and Spring Framework. This test framework
allows you to have a end-to-end test within a JUnit container. You can select your own Spring ApplicationContext and 
use this in your tests. (By controlling the Spring ApplicationContext in your test, you could be able to have Mockito 
mocks as Spring beans.)

This framework is inspired on a how to I created for testing Spring, Jersey and Mockito

- [Jersey 2](https://github.com/Hylke1982/jersey2-spring-test-example/tree/master)
- [Jersey 2.8+](https://github.com/Hylke1982/jersey2-spring-test-example/tree/jersey-2.8)

Requirements
------------

- JDK 1.6+
- Jersey 2.8+

Usage
-----

Description of the usage of this test framework

Maven
-----

You're able to get this library from the maven repository using the following dependency declaration.

```xml
<dependency>
<groupId>nl.bstoi.jersey.test-framework</groupId>
<artifactId>jersey-spring-exposed-test-framework-core</artifactId>
<version>2.24</version>
<scope>test</scope>
</dependency>
```

Creating a test
---------------

```java
// Test example using mockito
public class SomeResourceTest extends SpringContextJerseyTest {

    public static final String MOCK_SPRING_APPLICATIONCONTEXT = "classpath:mockApplicationContext.xml";


    private SomeService mockSomeService;

    @Override
    protected Application configure() {
        // Configure your application
        ResourceConfig resourceConfig = new SomeApplication();
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);
        resourceConfig.property("contextConfigLocation", MOCK_SPRING_APPLICATIONCONTEXT); // Set which application context to use
        return resourceConfig;
    }

    @Before
    public void setUp() throws Exception {
        // Setup and start jersey
        super.setUp();
        mockSomeService = (SomeService) getSpringApplicationContext().getBean("someService");
        Assert.assertNotNull(mockSomeService);
    }

    @After
    public void after() throws Exception {
        // Stop jersey
        super.tearDown();
    }
    

    @Test
    public void testDoSomethingWithException() {
        // Do test
        Mockito.doThrow(new RuntimeException()).when(mockSomeService).doSomething();
        Response response = target("someaction").request().get(Response.class);
        Mockito.verify(mockSomeService, Mockito.times(1)).doSomething();  // Validate if doSomething() is called
        Assert.assertEquals(500, response.getStatus()); // Expect 500 when exception is thrown
    }


}
```

Todo
----

- Add examples in code
