/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */

package org.apache.directory.server.dns.io.decoder;


import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.directory.api.util.Strings;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.mina.core.buffer.IoBuffer;


/**
 * A decoder for AAAA records.  AAAA records are encoded as per RFC-3596:
 * 
 * <pre>
 *   2.2. AAAA data format
 *
 *     A 128 bit IPv6 address is encoded in the data portion of an AAAA
 *     resource record in network byte order (high-order byte first).
 * </pre>
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class IPv6RecordDecoder implements RecordDecoder
{

    public Map<String, Object> decode( IoBuffer byteBuffer, short length ) throws IOException
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        byte[] addressBytes = new byte[length];
        byteBuffer.get( addressBytes );
        attributes.put( Strings.toLowerCase( DnsAttribute.IP_ADDRESS ), InetAddress.getByAddress( addressBytes ) );
        
        return attributes;
    }

}
