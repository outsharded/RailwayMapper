package com.outsharded.railwaymapper;

import java.util.*;

public class RailLine {
    public final int networkId;
    public final String color;
    public final List<int[]> vertices; // [x, y, z] points where direction changes
    
    public RailLine(int networkId, String color) {
        this.networkId = networkId;
        this.color = color;
        this.vertices = new ArrayList<>();
    }
    
    public void addVertex(int x, int y, int z) {
        // Only add if different from last vertex
        if (vertices.isEmpty() || !isSameAs(vertices.get(vertices.size() - 1), x, y, z)) {
            vertices.add(new int[]{x, y, z});
        }
    }
    
    private boolean isSameAs(int[] v, int x, int y, int z) {
        return v[0] == x && v[1] == y && v[2] == z;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("networkId", networkId);
        map.put("color", color);
        List<List<Integer>> vertexList = new ArrayList<>();
        for (int[] v : vertices) {
            vertexList.add(Arrays.asList(v[0], v[1], v[2]));
        }
        map.put("vertices", vertexList);
        return map;
    }
}
