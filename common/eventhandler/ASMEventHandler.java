/*
 * Minecraft Forge
 * Copyright (c) 2016-2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.fml.common.eventhandler;

import com.google.common.collect.Maps;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.logging.log4j.ThreadContext;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;

public class ASMEventHandler implements IEventListener
{
    private static int IDs = 0;
    private static final String HANDLER_DESC = Type.getInternalName(IEventListener.class);
    private static final String HANDLER_FUNC_DESC = Type.getMethodDescriptor(IEventListener.class.getDeclaredMethods()[0]);
    private static final ASMClassLoader LOADER = new ASMClassLoader();
    private static final HashMap<Method, ASMEventHandlerWrapper> cache = Maps.newHashMap();
    private static final boolean GETCONTEXT = Boolean.parseBoolean(System.getProperty("fml.LogContext", "false"));

    private final IEventListener handler;
    private final SubscribeEvent subInfo;
    private final boolean paralellRun;
    private ModContainer owner;
    private String readable;
    private java.lang.reflect.Type filter = null;

    @Deprecated
    public ASMEventHandler(Object target, Method method, ModContainer owner) throws Exception
    {
        this(target, method, owner, false);
    }

    public ASMEventHandler(Object target, Method method, ModContainer owner, boolean isGeneric) throws Exception
    {
        this.owner = owner;
        subInfo = method.getAnnotation(SubscribeEvent.class);
        paralellRun = subInfo.parallelExecution();
        handler = createWrapper(method, target,paralellRun);
        readable = "ASM: " + target + " " + method.getName() + Type.getMethodDescriptor(method);
        if (isGeneric)
        {
            java.lang.reflect.Type type = method.getGenericParameterTypes()[0];
            if (type instanceof ParameterizedType)
            {
                filter = ((ParameterizedType)type).getActualTypeArguments()[0];
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void invoke(Event event)
    {
        if (GETCONTEXT)
            ThreadContext.put("mod", owner == null ? "" : owner.getName());
        if (handler != null)
        {
            if (!event.isCancelable() || !event.isCanceled() || subInfo.receiveCanceled())
            {
                if (filter == null || filter == ((IGenericEvent)event).getGenericType())
                {
                    handler.invoke(event);
                }
            }
        }
        if (GETCONTEXT)
            ThreadContext.remove("mod");
    }

    public EventPriority getPriority()
    {
        return subInfo.priority();
    }

    public ASMEventHandlerWrapper createWrapper(Method callback, Object target, boolean paralellRun)
    {
        if (cache.containsKey(callback))
        {
            return cache.get(callback);
        }
        boolean isStatic = Modifier.isStatic(callback.getModifiers());
        ASMEventHandlerWrapper wr = isStatic ? new ASMEventHandlerWrapper(callback, paralellRun)
                : new ASMEventHandlerWrapper(callback, target, paralellRun);
        cache.put(callback, wr);
        return wr;
    }

    private String getUniqueName(Method callback)
    {
        return String.format("%s_%d_%s_%s_%s", getClass().getName(), IDs++,
                callback.getDeclaringClass().getSimpleName(),
                callback.getName(),
                callback.getParameterTypes()[0].getSimpleName());
    }

    private static class ASMClassLoader extends ClassLoader
    {
        private ASMClassLoader()
        {
            super(ASMClassLoader.class.getClassLoader());
        }

        public Class<?> define(String name, byte[] data)
        {
            return defineClass(name, data, 0, data.length);
        }
    }

    public String toString()
    {
        return readable;
    }

    @Override
    public boolean parallelExecution() {
        return paralellRun;
    }
}
