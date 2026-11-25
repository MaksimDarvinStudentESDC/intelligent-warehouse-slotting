package com.warehouse.slotting.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.test.util.ReflectionTestUtils;
public final class PlacementFixtures {
    private PlacementFixtures() {}
    public static Warehouse warehouse(long id) {
        var w = new Warehouse(); ReflectionTestUtils.setField(w,"id",id); return w;
    }
    public static Batch batch(Warehouse w) {
        var p = new Product(w,"SKU","Item",100,100,100,new BigDecimal("2"),null,null,OutboundFlow.PICKING);
        ReflectionTestUtils.setField(p,"id",3L);
        var b = new Batch(p,"B-1",10,LocalDate.of(2026,11,25),null);
        ReflectionTestUtils.setField(b,"id",1L); return b;
    }
    public static Location location(Warehouse w) {
        var l = new Location();
        ReflectionTestUtils.setField(l,"id",6L);
        ReflectionTestUtils.setField(l,"warehouse",w);
        ReflectionTestUtils.setField(l,"code","ST-06");
        ReflectionTestUtils.setField(l,"version",0L);
        ReflectionTestUtils.setField(l,"volumeCapacityDm3",new BigDecimal("100"));
        ReflectionTestUtils.setField(l,"maxWeightKg",new BigDecimal("100"));
        return l;
    }
}
