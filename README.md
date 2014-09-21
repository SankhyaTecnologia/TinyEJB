TinyEJB
=======

The main objective of TinyEJB is to be an simple alternative for old J2EE 1.4 applications that use EJB 2.1 but need to run on
modern JEE application servers that don't supports this specification anymore (or is very difficult to configure).

On this very first version, TinyEJB supports Stateless and Stateful Session Beans, including Container Managed Transactions (since JTA 
is present on the runtime environment).

For now, TinyEJB does NOT support:

* EJB as WebService end points
* Timed Beans
* Message-Driven beans
* EntityBeans (believe me, they are out there!)

Of course, any help is welcome to fill these gaps.

For Stateless and Stateful Session Beans, TinyEJB implements EJB 2.1 spec with some cool additions, like Singleton Stateless, automatic 
statefull method call synchronization (avoids the well known 'No concurrent calls on Stateful...' error message).


Examples
========

To see TinyEJB in action, check the test folder and execute them as plain Java Application (there are mocks for everything it needs).

Additionally, it depends on some JARs, as follows:

J2EE 1.4 interfaces 
    http://www.java2s.com/Code/JarDownload/j2ee/j2ee-1.4.jar.zip

JDom 1.1.3 
    http://www.jdom.org/dist/binary/archive/jdom-1.1.3.zip
