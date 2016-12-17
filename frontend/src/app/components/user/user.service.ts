import { Injectable } from '@angular/core';
import {Http} from "@angular/http";
import 'rxjs/add/operator/map';
import {Observable} from "rxjs";

@Injectable()
export class UserService {

  constructor(private http: Http) { }

  getUser(userId: number): Observable<User> {
    return this.http.get(`/api/users/${userId}`)
      .map(res => res.json())
  }

  getUsers(): Observable<Array<User>> {
    return this.http.get('/api/users')
      .map(res => res.json())
  }

  saveUser(username: String): Observable<UserWithPassword> {
    return this.http.post('/api/users/', {username: username})
      .map(res => res.json())
  }

  resetPassword(id: number): Observable<UserWithPassword> {
    return this.http.post(`/api/users/${id}/resetPassword`, {})
      .map(res => res.json())
  }

}

export class User {
  constructor(public id: number, public username: string) {}
}

export class UserWithPassword extends User {
  constructor(public id: number, public username: string, public password: string) {
    super(id, username);
  }
}

export class NewUser extends User {
  constructor() {
    super(null, null);
  }
}
