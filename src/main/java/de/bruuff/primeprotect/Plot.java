/*
 * This file is part of the plugin PrimeProtect for Sponge licensed under the MIT License (MIT).
 *
 * Copyright (c) 2015 Florian Brunzlaff
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bruuff.primeprotect;

import org.spongepowered.api.world.World;
import java.util.*;

public class Plot {
    private int id;
    private Optional<PlotOwner> optOwner;
    private World world;
    private Optional<Plot> optParent;
    private int depth;
    private PlotPoint centroid;
    private Integer minX, minZ, maxX, maxZ;
    private Set<PlotLine> borderBlockLines;

    private List<PlotPoint> vertices;

    public Plot(int id, Optional<PlotOwner> optOwner, World world, Plot parent) {
        this.id = id;
        this.optOwner = optOwner;
        this.world = world;
        this.optParent = Optional.empty();
        if(parent != null){
            this.optParent = Optional.of(parent);
            this.depth = parent.getDepth() + 1;
        }else{
            this.depth = 0; // We are generating Wilderness here
        }
        this.vertices = new ArrayList<>();
        this.centroid = new PlotPoint(0,0);
    }

    public Plot(int id, Optional<PlotOwner> optOwner, World world, String verticesString, int depth) {
        this.id = id;
        this.optOwner = optOwner;
        this.world = world;
        this.optParent = Optional.empty();
        this.depth = depth;
        this.vertices = getVerticesList(verticesString);
        this.centroid = calcCentroid();
    }

    public Plot(int id, Optional<PlotOwner> optOwner, World world, String verticesString, PlotPoint centroid, int depth, Optional<Plot> parent, int minX, int minZ, int maxX, int maxZ) {
        this.id = id;
        this.optOwner = optOwner;
        this.world = world;
        this.optParent = parent;
        this.depth = depth;
        this.vertices = getVerticesList(verticesString);
        this.centroid = centroid;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
    }

    public Response addPoint(PlotPoint vertex){
        if(vertices.size() > 0){
            PlotPoint lastVertex = vertices.get(vertices.size()-1);
            if(doAlign(vertex, lastVertex)){
                if(!intersectsBorder(new PlotLine(vertex, lastVertex))){
                    vertices.add(vertex);
                    update();
                    return Response.SUCCESS;
                }else return Response.FAILURE_INTERSECTS_BORDER;
            }else{
                return Response.FAILURE_BAD_ALIGNMENT;
            }
        }else{
            vertices.add(vertex);
            update();
            return Response.SUCCESS;
        }
    }

    private boolean doAlign(PlotPoint vertex1, PlotPoint vertex2){
        double distanceX = vertex1.getX() - vertex2.getX();
        double distanceY = vertex1.getZ() - vertex2.getZ();
        double ratio;
        if(distanceX != 0){
            ratio = Math.abs(distanceY / distanceX);
        }else if(distanceY != 0){
            ratio = Math.abs(distanceX / distanceY);
        }else return false; //Vertices are the same.
        if(ratio > 1) ratio = 1/ratio;

        if( (ratio * 1) != Math.round(ratio * 1) ){
            return false;
        }

        if(optParent.isPresent()){
            Plot parent = optParent.get();
            if(!parent.contains(new PlotLine(vertex1, vertex2))) {
                return false;
            }
        }
        return true;
    }

    public boolean isComplete(){
        PlotPoint firstVertex = vertices.get(0);
        PlotPoint lastVertex = vertices.get(vertices.size()-1);
        return doAlign(firstVertex, lastVertex);
    }

    private boolean intersectsBorder(PlotLine line){
        List<PlotLine> borderLines = getBorderLines();
        if(optParent.isPresent()) {
            borderLines.addAll(optParent.get().getBorderLines());
        }
        for(PlotLine borderLine : borderLines){
            if(borderLine.equals(line)) continue;
            if(borderLine.intersection(line).isPresent()){
                PlotPoint intersection = borderLine.intersection(line).get();
                if(!intersection.equals(borderLine.getP1()) && !intersection.equals(borderLine.getP2()) && !intersection.equals(line.getP1()) && !intersection.equals(line.getP2())){
                    return true;
                }
            }
        }
        return false;
    }


    public boolean isValidShape(){
        //This could probably be a bit better optimized. But we are not using it often, so its fine for now.
        List<PlotLine> borderLines = getBorderLines();
        if(optParent.isPresent()) {
            borderLines.addAll(optParent.get().getBorderLines());
        }
        for(PlotLine borderLine : borderLines){
            if(intersectsBorder(borderLine)) return false;
        }
        return true;
    }


    public static Plot wilderness(World world){
        return new Plot(-1, Optional.of(new PlotOwner(Group.everyone())), world, null);
    }

    public List<PlotPoint> getVertices() {
        return vertices;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName(){
        String plotName = "[Vacant]";
        if(optOwner.isPresent()){
            PlotOwner owner = optOwner.get();
            plotName = owner.getName();
        }
        return plotName;
    }

    public World getWorld() {
        return world;
    }

    public Optional<Plot> getParent() {
        return optParent;
    }

    public List<Plot> getParentChain(){
        boolean hasParent = true;
        int max = 100;
        List<Plot> parentChain = new ArrayList<>();
        Plot currentPlot = this;
        while(hasParent && max-- > 0){
            if(currentPlot.getParent().isPresent()){
                currentPlot = currentPlot.getParent().get();
                parentChain.add(currentPlot);
            }else{
                hasParent = false;
            }
        }
        return parentChain;
    }

    public void setParent(Plot parent) {
        this.optParent = Optional.of(parent);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public Optional<PlotOwner> getOwner() {
        return optOwner;
    }

    //Shortfor testing optional parent and owner separately
    public PlotOwner getCurrentOwner(){
        if(optOwner.isPresent()){
            return optOwner.get();
        }else{
            if(optParent.isPresent()){
                if(optParent.get().getOwner().isPresent()){
                    return optParent.get().getOwner().get();
                }
            }else{
                return new PlotOwner(Group.everyone()); // Vacant lot in wilderness
            }
        }
        return null; //If this happens something during plot claiming got messed up. (Vacant lots cant have child plots)
    }

    public void setOwner(PlotOwner owner) {
        this.optOwner = Optional.of(owner);
    }

    public int getDepth() {
        return depth;
    }

    public boolean contains(PlotPoint point){
        if(id < 0) return true; //Wilderness contains everything.
        //Simple bounding box check
        if(minX == null) update();
        if(minX != null && point.getX() < minX || point.getZ() < minZ || point.getX() > maxX || point.getX() > maxZ) return false;
        //Now connect each border line with the first vertex to form a triangle. Then count in how many we are in.
        List<PlotPoint[]> triangles = new ArrayList<>();
        for(int i = 0; i < vertices.size(); i++){   //Any edge but the last
            if(i != vertices.size() - 1){
                if(new PlotLine(vertices.get(i), vertices.get(i+1)).contains(point, true)) return true; //Anything on the border is automatically inside.
            }else{
                if(new PlotLine(vertices.get(i), vertices.get(0)).contains(point, true)) return true; //Anything on the border is automatically inside.
            }
            if(i == 0 || i == vertices.size() - 1) continue; //triangle to the start vertex would make no sense.
            PlotPoint[] triangle = {vertices.get(0),vertices.get(i), vertices.get(i+1)};
            triangles.add(triangle);
        }
        double counter = 0;
        for(PlotPoint[] triangle : triangles){
            int s1 = (int) Math.signum(point.cross(triangle[0], triangle[1])), 
                s2 = (int) Math.signum(point.cross(triangle[1], triangle[2])), 
                s3 = (int) Math.signum(point.cross(triangle[2], triangle[0]));
            if(s1 == 0 && s2 == 0 && s3 == 0) continue; //Remove any triangles that have no surface area
            if(s1 == s2 && s2 == s3){
                counter++;
            }else if( s1 <= 0 && s2 <= 0 && s3 <= 0 ){  //if we are standing on a border, we will also catch the other
                counter += 0.5;
            }else if( s1 >= 0 && s2 >= 0 && s3 >= 0){
                counter -= 0.5;
            }
        }
        //Even number -> not in, odd number -> in
        return counter % 2 != 0;
    }

    public boolean contains(PlotLine line){
        //If it crosses any border of the plot, it can't be fully contained.
        for(PlotLine borderLine : this.getBorderLines()){
            Optional<PlotPoint> optIntersection = line.intersection(borderLine);
            if(optIntersection.isPresent()){
                PlotPoint intersection = optIntersection.get();
                //We allow touching the border for this.
                if(line.contains(intersection, true)) continue;
                return false;
            }
        }
        //If its either completely in or completely out, we can just test one point.
        return this.contains(line.getP1());
    }

    public PlotPoint getCentroid() {
        if(centroid == null){
            centroid = calcCentroid();
        }
        return centroid;
    }

    public Set<PlotLine> getBorderBlocksLines(){
        if(borderBlockLines == null || (borderBlockLines.isEmpty() && vertices.size() > 1)){
            update();
        }
        return borderBlockLines;
    }

    private boolean isClockwise(){
        double sum = 0;
        for(PlotLine plotline: this.getBorderLines()){
            sum += (plotline.getP2().getX()-plotline.getP1().getX()) * (plotline.getP2().getZ()+plotline.getP1().getZ());
        }
        return sum > 0;
    }

    private void update(){
        //Updating Centroid
        if(vertices.size() >= 2){
            centroid = calcCentroid();
        }else if(vertices.size() == 1){
            centroid = vertices.get(0);
        }else{
            return;
        }

        //Updating bounding box
        for( PlotPoint point : vertices){
            if(minX == null || minX > point.getX()) minX = (int) point.round().getX();
            if(minZ == null || minZ > point.getZ()) minZ = (int) point.round().getZ();
            if(maxX == null || maxX < point.getX()) maxX = (int) point.round().getX();
            if(maxZ == null || maxZ < point.getZ()) maxZ = (int) point.round().getZ();
        }

        //Update BorderBlockLines
        borderBlockLines = new HashSet<>();
        boolean clockwise = this.isClockwise();
        for(PlotLine line : this.getBorderLines()){
            PlotPoint current = line.getP1();
            PlotPoint end = line.getP2();
            boolean start = true;
            while(!current.equals(end)){
                PlotPoint direction = new PlotPoint(0, 0);
                if(current.getX() < end.getX()) direction.setX(1);
                else if(current.getX() > end.getX()) direction.setX(-1);
                if(current.getZ() < end.getZ()) direction.setY(1);
                else if(current.getZ() > end.getZ()) direction.setY(-1);
                if(start){
                    start = false;
                }else{
                    current.add(direction);
                }

                if(clockwise){
                    if(direction.getX() == -1) borderBlockLines.add(new PlotLine(current.getX()+1, current.getZ()-0, current.getX()-0, current.getZ()-0));
                    if(direction.getZ() == -1) borderBlockLines.add(new PlotLine(current.getX()+1, current.getZ()+1, current.getX()+1, current.getZ()-0));
                    if(direction.getX() == 1) borderBlockLines.add(new PlotLine(current.getX()-0, current.getZ()+1, current.getX()+1, current.getZ()+1));
                    if(direction.getZ() == 1) borderBlockLines.add(new PlotLine(current.getX()-0, current.getZ()-0, current.getX()-0, current.getZ()+1));
                }else{
                    if(direction.getX() == -1) borderBlockLines.add(new PlotLine(current.getX()-0, current.getZ()+1, current.getX()+1, current.getZ()+1));
                    if(direction.getZ() == -1) borderBlockLines.add(new PlotLine(current.getX()-0, current.getZ()-0, current.getX()-0, current.getZ()+1));
                    if(direction.getX() == 1) borderBlockLines.add(new PlotLine(current.getX()+1, current.getZ()-0, current.getX()-0, current.getZ()-0));
                    if(direction.getZ() == 1) borderBlockLines.add(new PlotLine(current.getX()+1, current.getZ()+1, current.getX()+1, current.getZ()-0));
                }
            }
        }
    }

    private List<PlotLine> getBorderLines(){
        List<PlotLine> borderLines = new ArrayList<>();
        if(vertices.size() <= 1) return borderLines;
        for(int i = 0; i < vertices.size() - 1; i++){
            borderLines.add(new PlotLine(vertices.get(i).getX(), vertices.get(i).getZ(), vertices.get(i+1).getX(), vertices.get(i+1).getZ()));
        }
        borderLines.add(new PlotLine(vertices.get(vertices.size()-1).getX(), vertices.get(vertices.size()-1).getZ(), vertices.get(0).getX(), vertices.get(0).getZ()));
        return borderLines;
    }



    public PlotPoint calcCentroid(){
        if(vertices == null || vertices.size() == 0){
            return null;
        }else if(vertices.size() == 1){
            return vertices.get(0);
        }else if(vertices.size() == 2){
            PlotPoint p1 = vertices.get(0);
            PlotPoint p2 = vertices.get(1);
            return new PlotPoint((p1.getX() + p2.getX())/2, (p1.getZ() + p2.getZ())/2);
        }else{
            double px = 0, py = 0, area = 0;
            int n = vertices.size();
            vertices.add(vertices.get(0));

            for(int i = 0; i <= n-1; i++){
                area += 0.5 * (vertices.get(i).getX() * vertices.get(i+1).getZ() - vertices.get(i+1).getX() * vertices.get(i).getZ());
            }

            for(int i = 0; i <= n-1; i++){
                px += (vertices.get(i).getX() + vertices.get(i+1).getX()) * (vertices.get(i).getX() * vertices.get(i+1).getZ() - vertices.get(i+1).getX() * vertices.get(i).getZ());
            }
            px = px / (6 * area);

            for(int i = 0; i <= n-1; i++){
                py += (vertices.get(i).getZ() + vertices.get(i+1).getZ()) * (vertices.get(i).getX() * vertices.get(i+1).getZ() - vertices.get(i+1).getX() * vertices.get(i).getZ());
            }
            py = py / (6 * area);

            vertices.remove(n);
            return(new PlotPoint(px, py).round());
        }
    }

    public String getVerticesString(){
        String output = "";
        for( PlotPoint point : vertices){
            output += "[" + point.getX() + "," + point.getZ() + "]";
        }
        return output;
    }

    public static List<PlotPoint> getVerticesList(String vertexString){
        List<PlotPoint> verticesList = new ArrayList<>();
        vertexString = vertexString.substring(1,vertexString.length()-1);
        if(vertexString.contains("][")){
            for(String position : vertexString.split("\\]\\[")){
                if(position.contains(",")){
                    int x = (int) Double.parseDouble(position.split(",")[0]);
                    int z = (int) Double.parseDouble(position.split(",")[1]);
                    verticesList.add(new PlotPoint(x, z));
                }
            }
        }
        return verticesList;
    }

    public boolean equals(Object o){
        if(o instanceof Plot){
            Plot p = (Plot) o;
            return (this.id == p.getId());
        }else{
            return false;
        }
    }
}

class PlotPoint implements Comparable<PlotPoint> {

    public void setX(double x) {
        this.x = x;
    }
    public void setY(double z) {
        this.z = z;
    }

    double x, z;

    public PlotPoint(double x, double z) {
        this.x = x;
        this.z = z;
    }

    public void add(PlotPoint p){
        this.x += p.getX();
        this.z += p.getZ();
    }

    public double getX() {
        return x;
    }

    public double getZ() {
        return z;
    }

    public PlotPoint round(){
        return new PlotPoint(Math.round(this.x), Math.round(this.z));
    }

    public double cross(PlotPoint point1, PlotPoint point2) {
        return (point1.getX() - x) * (point2.getZ() - z) - (point1.getZ() - z) * (point2.getX() - x);
    }

    public int compareTo(PlotPoint p) {
        if (this.x == p.x) {
            return (int) Math.round(this.z - p.z);
        } else {
            return (int) Math.round(this.x - p.x);
        }
    }
    public String toString() {
        return "(" + (int) this.x + ", " + (int) this.z + ")";
    }

    public boolean equals(Object o) {
        if(o instanceof PlotPoint){
            PlotPoint p = (PlotPoint) o;
            return this.x == p.getX() && this.z == p.getZ();
        }else{
            return false;
        }
    }

    public int hashCode(){
        return ( (int) this.round().getX() * (int) Math.pow(10, 32 - String.valueOf(this.x).length())) + (int) this.round().getZ();
    }
}

class PlotLine {
    PlotPoint p1, p2;

    public PlotLine(PlotPoint p1, PlotPoint p2) {
        this.p1 = p1;
        this.p2 = p2;
    }

    public PlotLine(double x1, double y1, double x2, double y2) {
        this.p1 = new PlotPoint(x1, y1);
        this.p2 = new PlotPoint(x2, y2);
    }

    public PlotPoint getP1() {
        return p1;
    }

    public PlotPoint getP2() {
        return p2;
    }

    public String toString() {
        return "[" + this.p1.toString() + "|" + this.p2.toString() + "]";
    }

    public Optional<PlotPoint> intersection(PlotLine line2){
        PlotPoint p3 = line2.getP1(), p4 = line2.getP2();
        double d = (p1.getX()-p2.getX())*(p3.getZ()-p4.getZ()) - (p1.getZ()-p2.getZ())*(p3.getX()-p4.getX());
        if(d == 0) return Optional.empty();

        double xi = ((p3.getX()-p4.getX())*(p1.getX()*p2.getZ()-p1.getZ()*p2.getX())-(p1.getX()-p2.getX())*(p3.getX()*p4.getZ()-p3.getZ()*p4.getX()))/d;
        double zi = ((p3.getZ()-p4.getZ())*(p1.getX()*p2.getZ()-p1.getZ()*p2.getX())-(p1.getZ()-p2.getZ())*(p3.getX()*p4.getZ()-p3.getZ()*p4.getX()))/d;

        if ((xi < Math.min(p1.getX(),p2.getX()) || xi > Math.max(p1.getX(),p2.getX())) ||
                (zi < Math.min(p1.getZ(),p2.getZ()) || zi > Math.max(p1.getZ(),p2.getZ()))) return Optional.empty();

        if ((xi < Math.min(p3.getX(),p4.getX()) || xi > Math.max(p3.getX(),p4.getX())) ||
                (zi < Math.min(p3.getZ(),p4.getZ()) || zi > Math.max(p3.getZ(),p4.getZ()))) return Optional.empty();

        return Optional.of(new PlotPoint(xi,zi));
    }


    public boolean contains(PlotPoint point, boolean edgeAllowed){

        if(point.equals(p1) || point.equals(p2)){
            return edgeAllowed;
        }

        double distX = p2.getX() - p1.getX();
        double distZ = p2.getZ() - p1.getZ();

        double dist2X = p2.getX() - point.getX();
        double dist2Z = p2.getZ() - point.getZ();
        if(distX != 0) {
            if(dist2X != 0 && Math.signum(dist2X) == Math.signum(distX)){
                return Math.abs(dist2X) < Math.abs(distX) && distZ / distX == dist2Z / dist2X;
            }
        }else if(distZ != 0) {
            if(dist2Z != 0 && Math.signum(dist2Z) == Math.signum(distZ)){
                return Math.abs(dist2Z) < Math.abs(distZ) && distX / distZ == dist2X / dist2Z;
            }
        }
        return false; //should be unreachable, but whatever.
    }
/*
    public double cross(PlotPoint point){
        return (p1.getX() - point.getX()) * (p2.getZ() - point.getZ()) - (p1.getZ() - point.getZ()) * (p2.getX() - point.getX());
    }
*/
    public boolean equals(Object o) {
        if(o instanceof PlotLine){
            PlotLine l = (PlotLine) o;
            return this.p1.equals(l.getP1()) && this.p2.equals(l.getP2());
        }else{
            return false;
        }
    }

    public int hashCode(){
        return    (int) this.p1.round().getX() * (int) Math.pow(10, 32 - String.valueOf(this.p1.getX()).length())
                + (int) this.p1.round().getZ() * (int) Math.pow(10, 24 - String.valueOf(this.p1.getZ()).length())
                + (int) this.p2.round().getX() * (int) Math.pow(10, 16 - String.valueOf(this.p2.getX()).length())
                + (int) this.p2.round().getZ() * (int) Math.pow(10, 8 - String.valueOf(this.p2.getZ()).length());
    }
}