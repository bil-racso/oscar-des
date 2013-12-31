/*******************************************************************************
 * OscaR is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *   
 * OscaR is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License  for more details.
 *   
 * You should have received a copy of the GNU Lesser General Public License along with OscaR.
 * If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 ******************************************************************************/
package oscar.cp.test;
import oscar.cp.constraints.BinPackingFlow;
import oscar.cp.constraints.Eq;
import oscar.cp.core.CPVarInt;
import oscar.cp.core.CPStore;

import junit.framework.TestCase;

/**
 * @author Pierre Schaus pschaus@gmail.com
 */
public class TestBinPackingFlow extends TestCase {
	
	
	
    public TestBinPackingFlow(String name) {
        super(name);
        
    }
    	
	 /**
     * setUp() method that initializes common objects
     */
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * tearDown() method that cleanup the common objects
     */
    protected void tearDown() throws Exception {
        super.tearDown();
    }
/*    
    public void test1() { 
    	CPStore cp = new CPStore();
    	CPVarInt x [] = new CPVarInt[]{ CPVarInt.apply(cp, new int[]{0,1,2}),
    									CPVarInt.apply(cp, new int[]{0,1,2}),
    									CPVarInt.apply(cp, new int[]{0,1,2})};
    	int w [] = new int [] {4,5,6}; 
    	CPVarInt l [] = new CPVarInt[]{ CPVarInt.apply(cp, 0,8),
    									CPVarInt.apply(cp, 0,8)};
    	
    	cp.post(new BinPackingFlow(x, w, l));
    	assertTrue(cp.isFailed());
    }
    
    public void test2() { 	
    	CPStore cp = new CPStore();
    	CPVarInt x [] = new CPVarInt[]{ CPVarInt.apply(cp, new int[]{0,1}),
    									CPVarInt.apply(cp, new int[]{0,1}),
    									CPVarInt.apply(cp, new int[]{0,1})};
    	int w [] = new int [] {6,5,4}; 
    	CPVarInt l [] = new CPVarInt[]{ CPVarInt.apply(cp, 0,9),
    									CPVarInt.apply(cp, 0,6)};
    	
    	cp.post(new BinPackingFlow(x, w, l));
    	
    	assertTrue(!cp.isFailed());	
    }
 */   
    public void test3() { 	
    	CPStore cp = new CPStore();
    	CPVarInt x [] = new CPVarInt[]{ CPVarInt.apply(cp, 0, 1),
    									CPVarInt.apply(cp, 0, 1),
    									CPVarInt.apply(cp, 0, 1)};
    	int w [] = new int [] {6,5,4}; 
    	CPVarInt l [] = new CPVarInt[]{ CPVarInt.apply(cp, 0,9),
    									CPVarInt.apply(cp, 0,6)};
    	
    	CPVarInt c [] = new CPVarInt[]{ CPVarInt.apply(cp, 0,3),
				CPVarInt.apply(cp, 0,3)};
    	
    	cp.post(new BinPackingFlow(x, w, l,c));
    	cp.post(new Eq(x[0], 0));
    	
    	assertTrue(cp.isFailed());	
    }     
   /* 
    public void test4() { 
    	
    	CPStore cp = new CPStore();
    	CPVarInt x [] = new CPVarInt[]{ CPVarInt.apply(cp, new int[]{0,1,2}),
    									CPVarInt.apply(cp, new int[]{0,1,2}),
    									CPVarInt.apply(cp, new int[]{0,1,2})};
    	int w [] = new int [] {4,5,6}; 
    	CPVarInt l [] = new CPVarInt[]{ CPVarInt.apply(cp, 0,8),
    									CPVarInt.apply(cp, 0,8),
    									CPVarInt.apply(cp, 0,8)};
    	
    	cp.post(new BinPackingFlow(x, w, l));
    	
    	assertTrue(!cp.isFailed());
    	
    }
    */

}
