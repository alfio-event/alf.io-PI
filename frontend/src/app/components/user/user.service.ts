import { Injectable } from '@angular/core';
import 'rxjs/add/operator/map';
import {Observable} from "rxjs";
import { HttpClient } from '@angular/common/http';

@Injectable()
export class UserService {

  constructor(private http: HttpClient) { }

  getUser(userId: number): Observable<User> {
    return this.http.get<User>(`/api/internal/users/${userId}`);
  }

  getUsers(): Observable<Array<User>> {
    return this.http.get<Array<User>>('/api/internal/users');
  }

  saveUser(username: String): Observable<UserWithPassword> {
    return this.http.post<UserWithPassword>('/api/internal/users/', {username: username});
  }

  resetPassword(id: number): Observable<UserWithPassword> {
    return this.http.post<UserWithPassword>(`/api/internal/users/${id}/resetPassword`, {});
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
