package ghIssues;

public class Issue3322ParentException extends Exception {}

class Issue3322ChildException extends Issue3322ParentException {}

class ExceptionParent extends Exception {}

class ExceptionChild extends ExceptionParent {}