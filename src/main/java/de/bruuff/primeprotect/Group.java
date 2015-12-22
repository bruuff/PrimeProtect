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

import org.spongepowered.api.text.format.TextColor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class Group {

    String name;
    Map<UUID, Rank> users;
    Optional<TextColor> chatColor;

    public Group(String name, UUID founder) {
        this.name = name;
        this.users = new HashMap<>();
        this.users.put(founder, Rank.OPERATOR);
        this.chatColor = Optional.empty();
    }

    public Group(String name, Map<UUID, Rank> users, Optional<TextColor> chatColor) {
        this.name = name;
        this.users = users;
        this.chatColor = chatColor;
    }

    public static Group everyone(){
        return new Group("Wilderness", new HashMap<>(), Optional.empty());
    }

    public String getName() {
        return name;
    }

    public Optional<TextColor> getChatColor() {
        return chatColor;
    }

    public Map<UUID, Rank> getUsers() {
        return users;
    }

    public boolean addUser(UUID userUUID, Rank rank){
        if(!users.containsKey(userUUID)){
            users.put(userUUID, rank);
            return true;
        }else{
            return false;
        }
    }

    public boolean removeUser(UUID userUUID){
        if(users.containsKey(userUUID)){
            users.remove(userUUID);
            return true;
        }else{
            return false;
        }
    }

    public boolean rankUser(UUID userUUID, Rank rank){
        if(users.containsKey(userUUID)){
            users.remove(userUUID);
            users.put(userUUID, rank);
            return true;
        }else{
            return false;
        }
    }

    public String getSerializedUsers(){
        String output = "";
        for(Map.Entry<UUID, Rank> entry : users.entrySet()){
            output += entry.getKey().toString() + "," + entry.getValue().name() + "|";
        }
        return output;
    }

}
