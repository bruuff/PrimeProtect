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

import org.spongepowered.api.Sponge;
import org.spongepowered.api.service.user.UserStorageService;
import java.util.Optional;
import java.util.UUID;

public class PlotOwner{
    UUID userUUID;
    Group group;

    public PlotOwner(UUID userUUID) {
        this.userUUID = userUUID;
    }

    public PlotOwner(Group group) {
        this.group = group;
    }

    public boolean isUser(){
        return (userUUID != null) ;
    }

    public boolean isGroup() {
        return (group != null);
    }

    public String getName(){
        String name = "Unknown";
        if(this.isUser()) {
            name = "Unknown Player";
            Optional<UserStorageService> optUserStorageService= Sponge.getGame().getServiceManager().provide(UserStorageService.class);
            if(optUserStorageService.isPresent()){
                UserStorageService userStorageService = optUserStorageService.get();
                if(userStorageService.get(userUUID).isPresent()){
                    name = userStorageService.get(userUUID).get().getName();
                }
            }
        }else if(this.isGroup()) {
            name = group.getName();
        }
        return name;
    }

    public boolean containsUser(UUID uuid, Rank rank){
        if(this.isUser()){
            return userUUID.equals(uuid);
        }else{
            if(group.getName().equals("Wilderness")){//This is the case for the wilderness permission group, hard coded for now.
                if(!rank.equals(Rank.MEMBER)){
                    Optional<UserStorageService> optUserStorageService= Sponge.getGame().getServiceManager().provide(UserStorageService.class);
                    if(optUserStorageService.isPresent()){
                        UserStorageService userStorageService = optUserStorageService.get();
                        if(userStorageService.get(uuid).isPresent()){
                            if(userStorageService.get(uuid).get().hasPermission("primeprotect.wilderness.claim")){
                                return true;
                            }
                        }
                    }
                    return false;
                }else{
                    return true;
                }
            }
            if(group.getUsers().containsKey(uuid)){
                if(rank.equals(Rank.OPERATOR)) {
                    if(group.getUsers().get(uuid).equals(Rank.OPERATOR)) return true;
                }else if(rank.equals(Rank.ASSISTANT)) {
                    if(!group.getUsers().get(uuid).equals(Rank.MEMBER)) return true;
                }else{
                    return true;
                }
            }
        }
        return false;
    }

    public String serialize(){
        String output;
        if(this.isUser()) output = "P:" + userUUID.toString();
        else if(this.isGroup()) output = "G:" + group.getName();
        else output = "";
        return output;
    }

}
