package org.obiba.magma.js.methods;

import java.util.Date;
import java.util.Set;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.obiba.magma.NoSuchValueSetException;
import org.obiba.magma.Value;
import org.obiba.magma.ValueSet;
import org.obiba.magma.VariableValueSource;
import org.obiba.magma.js.MagmaContext;
import org.obiba.magma.js.ScriptableValue;
import org.obiba.magma.support.MagmaEngineReferenceResolver;
import org.obiba.magma.type.DateType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

public final class GlobalMethods {

  private static final Logger log = LoggerFactory.getLogger(GlobalMethods.class);

  /**
   * Set of methods to be exposed as top-level methods (ones that can be invoked anywhere)
   */
  public static final Set<String> GLOBAL_METHODS = ImmutableSet.of("valueOf", "now", "log");

  /**
   * Creates an instance of {@code ScriptableValue} containing the current date and time.
   * 
   * @return an instance of {@code ScriptableValue} containing the current date and time.
   */
  public static ScriptableValue now(Context cx, Scriptable thisObj, Object[] args, Function funObj) {
    return new ScriptableValue(thisObj, DateType.get().valueOf(new Date()));
  }

  /**
   * Allows invoking {@code VariableValueSource#getValue(ValueSet)} and returns a {@code ScriptableValue}. Accessed as $
   * in javascript.
   * 
   * <pre>
   *   $('Participant.firstName')
   *   $('other-collection:SMOKER_STATUS')
   * </pre>
   * @return an instance of {@code ScriptableValue}
   */
  public static Scriptable valueOf(Context ctx, Scriptable thisObj, Object[] args, Function funObj) {
    if(args.length != 1) {
      throw new UnsupportedOperationException("$() expects exactly one argument: a variable name.");
    }

    MagmaContext context = MagmaContext.asMagmaContext(ctx);

    String name = (String) args[0];
    ValueSet valueSet = (ValueSet) context.peek(ValueSet.class);

    MagmaEngineReferenceResolver reference = MagmaEngineReferenceResolver.valueOf(name);

    // Find the named source
    final VariableValueSource source = reference.resolveSource(valueSet);

    // Tests whether this valueSet is in the same table as the referenced ValueTable
    if(reference.isJoin(valueSet)) {
      // Resolve the joined valueSet
      try {
        valueSet = reference.join(valueSet);
      } catch(NoSuchValueSetException e) {
        // Entity does not have a ValueSet in joined collection
        // Return a null value
        return new ScriptableValue(thisObj, source.getVariable().getValueType().nullValue());
      }
    }

    Value value = source.getValue(valueSet);
    return new ScriptableValue(thisObj, value);
  }

  /**
   * Allows 'info' level logging of messages and variables. Returns a {@code ScriptableValue}. Accessed as 'log' in
   * javascript.
   * 
   * <pre>
   *   log('My message')
   *   log($('org.obiba.onyx.lastExportDate'))
   *   log('The last export date: {}', $('org.obiba.onyx.lastExportDate'))
   * </pre>
   * @return an instance of {@code ScriptableValue}
   */
  public static Scriptable log(Context ctx, Scriptable thisObj, Object[] args, Function funObj) {
    if(args.length == 1) {
      if(args[0] instanceof ScriptableValue) {
        ScriptableValue arg = (ScriptableValue) args[0];
        log.info(arg.getValue().toString());
      } else {
        log.info(args[0].toString());
      }
    } else if(args.length == 2) {
      if(args[1] instanceof ScriptableValue) {
        ScriptableValue arg = (ScriptableValue) args[1];
        log.info(args[0].toString(), arg.getValue().toString());
      } else {
        log.info(args[0].toString(), args[1]);
      }
    } else {
      throw new UnsupportedOperationException("log() expects either one argument (object) or two arguments ('My message {}', object).");
    }
    return thisObj;
  }
}
