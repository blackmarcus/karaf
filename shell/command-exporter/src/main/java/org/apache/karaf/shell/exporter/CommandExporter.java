/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.shell.exporter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.CommandWithAction;
import org.apache.felix.gogo.commands.basic.AbstractCommand;
import org.apache.felix.service.command.CommandProcessor;
import org.apache.felix.service.command.Function;
import org.apache.karaf.shell.console.CompletableFunction;
import org.apache.karaf.shell.console.Completer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exports an Action as a command using a template for the action injects
 */
@SuppressWarnings("deprecation")
public class CommandExporter extends AbstractCommand implements CompletableFunction {
    private static Logger logger = LoggerFactory.getLogger(CommandExporter.class);

    private Action actionTemplate;
    private List<Completer> completers = new ArrayList<Completer>();

    public CommandExporter(Action actionTemplate)
    {
        this.actionTemplate = actionTemplate;
        addCompleters();
    }

    public Class<? extends Action> getActionClass()
    {
        return actionTemplate.getClass();
    }

    public Action createNewAction() {
        try {
            Action newAction = actionTemplate.getClass().newInstance();
            copyFields(newAction);
            return newAction;
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void copyFields(Action newAction) {
        Field[] fields = actionTemplate.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                Object value = field.get(actionTemplate);
                field.set(newAction, value);
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public static ServiceRegistration export(BundleContext context, Action actionTemplate)
    {
        Class<? extends Action> actionClass = actionTemplate.getClass();
        logger.info(actionClass.getName());
        Command cmd = actionClass.getAnnotation(Command.class);
        if (cmd == null)
        {
            throw new IllegalArgumentException("Action class is not annotated with @Command");
        }
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(CommandProcessor.COMMAND_SCOPE, cmd.scope());
        props.put(CommandProcessor.COMMAND_FUNCTION, cmd.name());
        CommandExporter command = new CommandExporter(actionTemplate);
        return context.registerService(new String[]{
                                                    Function.class.getName(),
                                                    CommandWithAction.class.getName(),
                                                    AbstractCommand.class.getName()
                                                    }, 
                                       command, props);
    }

    private void addCompleters() {
        for (Field field : actionTemplate.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (Completer.class.isAssignableFrom(field.getType())) {
                try {
                    this.completers.add((Completer)field.get(actionTemplate));
                } catch (Exception e) {
                    logger.warn("Error setting completer from field " + field.getName());
                }
            }
        }
    }

    @Override
    public List<Completer> getCompleters() {
        return completers;
    }

    @Override
    public Map<String, Completer> getOptionalCompleters() {
        return null;
    }

}
