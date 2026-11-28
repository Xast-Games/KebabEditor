package org.xast.kebab.behavior;

// TODO: to be replaced with ADT in the future...
public record PlainBehaviorProblem(String value) 
    implements BehaviorProblem 
{    
    @Override 
    public String getMessage() {
        return value;
    }
}
