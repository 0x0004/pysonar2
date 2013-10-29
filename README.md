## PySonar2 - an advanced static analyzer for Python

To understand it, please refer to my blog post:

    http://yinwang0.wordpress.com/2010/09/12/pysonar


### How to build

1. Download Jython

hg clone http://hg.python.org/jython

2. Checkout this repo, replace everything inside _src/org/python/indexer_ with
   the content of this repo

3. Delete the tests for the old indexer

    rm -rf tests/java/org/python/indexer

4. Build Jython

    ant jar-complete

5. Finished. PySonar2 is now inside _dist/jython.jar_.


### How to run?

PySonar2 is mainly designed as a library for Python IDEs and other tools, but
for your understanding of the library's usage, a demo program is built (most
credits go to Steve Yegge). To run it, use the following command line:

    java -classpath dist/jython.jar org.python.indexer.demos.HtmlDemo /usr/lib/python2.7 /usr/lib/python2.7

