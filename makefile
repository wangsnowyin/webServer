JCC = javac

default: HTTPServer.class

HTTPServer.class: HTTPServer.java
	$(JCC) HTTPServer.java

clean:
	@rm -f *.class
